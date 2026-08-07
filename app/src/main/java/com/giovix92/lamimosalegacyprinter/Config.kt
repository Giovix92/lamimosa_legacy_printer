package com.giovix92.lamimosalegacyprinter

/**
 * Fixed configuration for this device. Deliberately hardcoded (not entered at
 * first-run): this app only ever runs on one premises-bound legacy POS, so
 * baking the staff password in trades a small amount of "secret in the APK"
 * risk for one less thing to configure/lose on a device with no keyboard.
 *
 * If this ever needs to run on more than one device, or STAFF_PASSWORD
 * rotates, this is the only file that needs touching.
 */
object Config {
    const val ORDERS_API_BASE_URL = "https://www.lamimosapasticceria.com"

    // order.mjs's checkAuth() accepts either ADMIN_PASSWORD or STAFF_PASSWORD
    // via "Authorization: Bearer <password>" - this is ADMIN_PASSWORD (the
    // earlier "operai" value baked in here was wrong: that was descriptive
    // text in a code comment, not a literal quoted credential - confirmed via
    // curl that it 401s). Update here if this password is ever rotated.
    const val STAFF_PASSWORD = "Lamimosa1988!"
}
