package com.autel.sdk.debugtools.beans;

/**
 * Child item entity
 */
public class ChildItem {
    /**
     * Parent class name of this child item
     */
    protected int parentId;

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }
}
