package com.autel.sdk.debugtools.beans;

public abstract class ParentItem {
    /**
     * Whether expanded, default false
     */
    protected boolean isExpand = false;
    public boolean isExpand() {
        return isExpand;
    }

    public void setExpand(boolean expand) {
        isExpand = expand;
    }

    public abstract void printMessage();
}
