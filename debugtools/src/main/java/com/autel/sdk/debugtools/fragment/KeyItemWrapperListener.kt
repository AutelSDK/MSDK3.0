package com.autel.sdk.debugtools.fragment

/**
 * key and item value change listener or wrapper detection
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/9.
 */
interface KeyItemWrapperListener<T> {
    fun actionChange(t: T?)
}