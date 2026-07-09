package com.bazaarflipper.mayor;

import java.util.ArrayList;
import java.util.List;

public class MayorData {
    public static class Perk {
        public String name;
        public String description;
        public List<String> affectedCategories;

        public Perk(String name, String description, List<String> categories) {
            this.name = name;
            this.description = description;
            this.affectedCategories = categories != null ? categories : new ArrayList<>();
        }
    }

    private String name;
    private List<Perk> perks = new ArrayList<>();
    private long termStart = 0;
    private long termEnd = 0;

    public MayorData(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public List<Perk> getPerks() { return perks; }

    public void addPerk(Perk p) { perks.add(p); }

    public long getTermStart() { return termStart; }
    public void setTermStart(long t) { termStart = t; }
    public long getTermEnd() { return termEnd; }
    public void setTermEnd(long t) { termEnd = t; }

    public boolean isDerpy() {
        return "Derpy".equalsIgnoreCase(name);
    }
}
