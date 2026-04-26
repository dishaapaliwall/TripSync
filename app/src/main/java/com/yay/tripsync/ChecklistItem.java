package com.yay.tripsync;

public class ChecklistItem {

    private String id;
    private String name;
    private String category;
    private int quantity;
    private boolean checked;
    private String addedByUid;

    public ChecklistItem() {}

    public ChecklistItem(String id, String name, String category, int quantity, boolean checked) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.checked = checked;
    }

    public ChecklistItem(String id, String name, String category, int quantity, boolean checked, String addedByUid) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.checked = checked;
        this.addedByUid = addedByUid;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }

    public String getAddedByUid() { return addedByUid; }
    public void setAddedByUid(String addedByUid) { this.addedByUid = addedByUid; }
}
