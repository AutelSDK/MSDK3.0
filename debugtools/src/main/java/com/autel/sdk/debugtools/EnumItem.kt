package com.autel.sdk.debugtools

import java.io.Serializable

/**
 * SDK corresponding enumeration
 *
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/12/17.
 */

class EnumItem : Serializable {
    /**
     * Item description to display
     */
    private var description: String? = null

    /**
     * SDK enum
     */
    private var name: String? = null

    /**
     * Whether selected
     */
    private var selected = false
    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getDescription(): String? {
        return description
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    fun isSelected(): Boolean {
        return selected
    }

    fun setSelected(selected: Boolean) {
        this.selected = selected
    }

    companion object {
        private const val serialVersionUID = 876323262645176354L
    }
}