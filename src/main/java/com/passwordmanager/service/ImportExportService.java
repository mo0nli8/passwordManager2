package com.passwordmanager.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.opencsv.CSVReader;
import com.passwordmanager.crypto.CryptoUtil;
import com.passwordmanager.model.*;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Handles import and export in three formats:
 *   1. Own encrypted JSON  (export + import)
 *   2. Bitwarden JSON      (import only)
 *   3. KeePass CSV         (import only)
 */
public class ImportExportService {

    private final VaultService vaultService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportExportService(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports all entries to an AES-256-GCM encrypted JSON file.
     * The file is self-contained: it embeds a fresh salt so it can be decrypted
     * with only the master password (without knowing the vault key).
     */
    public void exportEncrypted(Path destination, SecretKey vaultKey, char[] masterPassword)
            throws Exception {
        // Collect all entries
        List<EntryListItem> items = vaultService.listAll();
        ArrayNode entriesNode = mapper.createArrayNode();
        for (EntryListItem item : items) {
            EntryDto dto = vaultService.getEntry(item.getId(), vaultKey);
            ObjectNode node = mapper.createObjectNode();
            node.put("type",     dto.getType().name());
            node.put("title",    dto.getTitle());
            node.put("category", dto.getCategoryName() != null ? dto.getCategoryName() : "");
            node.put("favorite", dto.isFavorite());
            ObjectNode fieldsNode = mapper.createObjectNode();
            dto.getFields().forEach(fieldsNode::put);
            node.set("fields", fieldsNode);
            node.put("tags",  String.join(",", dto.getTags()));
            entriesNode.add(node);
        }

        // Encrypt the JSON with a key derived from the master password
        byte[] plainJson  = mapper.writeValueAsBytes(entriesNode);
        byte[] exportSalt = CryptoUtil.generateSalt();
        SecretKey exportKey = CryptoUtil.deriveKey(masterPassword, exportSalt, 200_000);
        byte[] encrypted  = CryptoUtil.encrypt(plainJson, exportKey);

        // Final file: JSON wrapper with salt + ciphertext
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("salt",    CryptoUtil.toHex(exportSalt));
        root.put("data",    Base64.getEncoder().encodeToString(encrypted));
        mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), root);
    }

    // ── Import: own format ────────────────────────────────────────────────────

    public int importEncrypted(Path source, char[] masterPassword, SecretKey vaultKey)
            throws Exception {
        JsonNode root       = mapper.readTree(source.toFile());
        byte[]   salt       = CryptoUtil.fromHex(root.get("salt").asText());
        byte[]   encrypted  = Base64.getDecoder().decode(root.get("data").asText());
        SecretKey exportKey = CryptoUtil.deriveKey(masterPassword, salt, 200_000);
        byte[]   plainJson  = CryptoUtil.decrypt(encrypted, exportKey);

        ArrayNode entries = (ArrayNode) mapper.readTree(plainJson);
        return importFromJsonArray(entries, vaultKey);
    }

    // ── Import: Bitwarden JSON ─────────────────────────────────────────────────

    public int importBitwarden(Path source, SecretKey vaultKey) throws Exception {
        JsonNode root   = mapper.readTree(source.toFile());
        JsonNode items  = root.path("items");
        if (!items.isArray()) throw new IllegalArgumentException("Not a valid Bitwarden export");

        int count = 0;
        for (JsonNode item : items) {
            EntryDto dto = new EntryDto();
            dto.setTitle(item.path("name").asText("Untitled"));
            dto.setFavorite(item.path("favorite").asBoolean(false));

            int type = item.path("type").asInt(1);
            if (type == 1) { // login
                dto.setType(EntryType.LOGIN);
                JsonNode login = item.path("login");
                dto.setField("username", login.path("username").asText(""));
                dto.setField("password", login.path("password").asText(""));
                JsonNode uris = login.path("uris");
                if (uris.isArray() && uris.size() > 0) {
                    dto.setField("url", uris.get(0).path("uri").asText(""));
                }
            } else if (type == 2) { // secure note
                dto.setType(EntryType.NOTE);
                dto.setField("body", item.path("notes").asText(""));
            } else {
                continue; // skip card/identity import from Bitwarden for now
            }
            vaultService.createEntry(dto, vaultKey);
            count++;
        }
        return count;
    }

    // ── Import: KeePass CSV ────────────────────────────────────────────────────

    /**
     * Standard KeePass CSV columns: Account,Login Name,Password,Web Site,Comments
     */
    public int importKeePassCsv(Path source, SecretKey vaultKey) throws Exception {
        int count = 0;
        try (CSVReader reader = new CSVReader(new FileReader(source.toFile()))) {
            String[] header = reader.readNext(); // skip header
            if (header == null) return 0;
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length < 3) continue;
                EntryDto dto = new EntryDto();
                dto.setType(EntryType.LOGIN);
                dto.setTitle(safeGet(row, 0, "Untitled"));
                dto.setField("username", safeGet(row, 1, ""));
                dto.setField("password", safeGet(row, 2, ""));
                dto.setField("url",      safeGet(row, 3, ""));
                dto.setField("notes",    safeGet(row, 4, ""));
                vaultService.createEntry(dto, vaultKey);
                count++;
            }
        }
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int importFromJsonArray(ArrayNode entries, SecretKey vaultKey) throws Exception {
        int count = 0;
        for (JsonNode node : entries) {
            EntryDto dto = new EntryDto();
            dto.setType(EntryType.from(node.path("type").asText("LOGIN")));
            dto.setTitle(node.path("title").asText("Untitled"));
            dto.setCategoryName(node.path("category").asText(""));
            dto.setFavorite(node.path("favorite").asBoolean(false));
            String tags = node.path("tags").asText("");
            if (!tags.isBlank()) dto.setTags(Arrays.asList(tags.split(",")));
            node.path("fields").fields().forEachRemaining(e ->
                    dto.setField(e.getKey(), e.getValue().asText()));
            vaultService.createEntry(dto, vaultKey);
            count++;
        }
        return count;
    }

    private String safeGet(String[] arr, int idx, String def) {
        return (idx < arr.length && arr[idx] != null) ? arr[idx].trim() : def;
    }
}
