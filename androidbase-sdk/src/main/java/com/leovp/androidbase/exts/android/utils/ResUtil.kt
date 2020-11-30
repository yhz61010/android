package com.leovp.androidbase.exts.android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.system.API


/**
 * Return value associated with a particular resource ID
 */
class ResUtil(private val context: Context = app) {

    private val resource: Resources = context.resources

    /**
     * Return an integer associated with a particular resource ID.
     * @param id Resource ID
     **/
    fun getInt(id: Int) = resource.getInteger(id)

    /**
     * Return the string value associated with a particular resource ID.  It will be stripped of any styled text information.
     * @param id Resource ID
     **/
    fun getString(id: Int) = resource.getString(id)

    /**
     * Return the formatted string value associated with a particular resource ID.  It will be stripped of any styled text information.
     * @param id Resource ID
     **/
    fun getString(id: Int, vararg args: Any?) = String.format(resource.getString(id), *args)

    /**
     * Return the quantity string value associated with a particular resource ID.  It will be stripped of any styled text information.
     * @param id Resource ID
     **/
    fun getString(id: Int, quantity: Int) = resource.getQuantityString(id, quantity)

    /**
     * Return the formatted quantity string value associated with a particular resource ID.  It will be stripped of any styled text information.
     * @param id Resource ID
     **/
    fun getString(id: Int, quantity: Int, vararg args: Any?) = resource.getQuantityString(id, quantity, *args)

    /**
     * Return a boolean associated with a particular resource ID.  This can be used with any integral resource value, and will return true if it is non-zero.
     * @param id Resource ID
     **/
    fun getBool(id: Int) = resource.getBoolean(id)

    /**
     * Retrieve a dimensional for a particular resource ID.  Unit conversions are based on the current [DisplayMetrics] associated with the resources.
     * @param id Resource ID
     **/
    fun getDimension(id: Int) = resource.getDimension(id)

    /**
     * Retrieve a dimensional for a particular resource ID for use as a size in raw pixels.
     * @param id Resource ID
     **/
    fun getPixel(id: Int) = resource.getDimensionPixelSize(id)

    /**
     * Returns a color associated with a particular resource ID.
     * @param id Resource ID
     **/
    @SuppressLint("NewApi")
    fun getColor(id: Int) = if (API.ABOVE_M) context.getColor(id) else ContextCompat.getColor(context, id)

    /**
     * Returns a drawable object associated with a particular resource ID.
     * @param id Resource ID
     **/
    fun getDrawable(id: Int) = if (API.ABOVE_L) context.getDrawable(id) else ContextCompat.getDrawable(context, id)

}