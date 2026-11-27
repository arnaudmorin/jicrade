package fr.amorin.jicrade.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun stripEmailDomainRemovesAtDomain() {
        assertEquals("pierre-samuel.le-stang", "pierre-samuel.le-stang@corp.ovh.com".stripEmailDomain())
        assertEquals("john.doe", "john.doe@gmail.com".stripEmailDomain())
        assertEquals("user_123", "user_123@domain.org".stripEmailDomain())
        assertEquals("pierre-samuel.le-stang · ovhcloud.atlassian.net", "pierre-samuel.le-stang@corp.ovh.com · ovhcloud.atlassian.net".stripEmailDomain())
        assertEquals("Alert closed by alice via web", "Alert closed by alice@domain.com via web".stripEmailDomain())
        assertEquals("No email here", "No email here".stripEmailDomain())
        assertEquals("", "".stripEmailDomain())
    }
}
