package com.airchess.data

import platform.Foundation.NSUserDefaults

class UserDefaultsStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : SettingsStore {

    override fun getString(key: String, default: String?): String? = defaults.stringForKey(key) ?: default

    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (has(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    override fun getInt(key: String, default: Int): Int =
        if (has(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)

    private fun has(key: String): Boolean = defaults.objectForKey(key) != null
}
