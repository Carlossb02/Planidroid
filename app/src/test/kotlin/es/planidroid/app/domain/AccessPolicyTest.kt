package es.planidroid.app.domain
import org.junit.Test
import org.junit.Assert.*
class AccessPolicyTest{
    @Test fun firstLoginCannotEnterOffline(){assertFalse(AccessPolicy.allowed("uid",false,null))}
    @Test fun approvedAccountCanUseOffline(){assertTrue(AccessPolicy.allowed("uid",true,null))}
    @Test fun revokedAccountCannotEnter(){assertFalse(AccessPolicy.allowed("uid",true,false))}
    @Test fun onlineGrantEnablesAccess(){assertTrue(AccessPolicy.allowed("uid",false,true))}
    @Test fun guestNeverEnters(){assertFalse(AccessPolicy.allowed("local",true,true))}
}
