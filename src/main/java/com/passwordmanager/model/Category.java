package com.passwordmanager.model;

public class Category {
    private int    id;
    private String name;

    public Category() {}
    public Category(int id, String name) { this.id = id; this.name = name; }

    public int    getId()             { return id; }
    public void   setId(int id)       { this.id = id; }
    public String getName()           { return name; }
    public void   setName(String n)   { this.name = n; }

    @Override public String toString() { return name; }
}
