package com.videocalls.app.data.util

object PhoneNumberHelper {

    /**
     * Normalizes any Indian phone representation (e.g. "+91 94419 10285", "09441910285", "919441910285", "9441910285")
     * into the clean 10-digit Indian standard mobile number without country prefix.
     */
    fun cleanIndianNumber(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return ""
        val digits = rawNumber.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) {
            digits.takeLast(10)
        } else {
            digits
        }
    }

    /**
     * Checks if string is a valid 10-digit Indian mobile number (starts with 6, 7, 8, or 9).
     */
    fun isValidIndianNumber(phone: String?): Boolean {
        val clean = cleanIndianNumber(phone)
        return clean.length == 10 && clean[0] in '6'..'9'
    }

    /**
     * Formats 10-digit number for display: "94419 10285"
     */
    fun formatForDisplay(phone: String?): String {
        val clean = cleanIndianNumber(phone)
        return if (clean.length == 10) {
            "${clean.substring(0, 5)} ${clean.substring(5)}"
        } else {
            clean
        }
    }
}
