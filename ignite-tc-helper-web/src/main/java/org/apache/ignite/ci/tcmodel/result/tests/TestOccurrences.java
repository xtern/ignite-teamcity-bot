package org.apache.ignite.ci.tcmodel.result.tests;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;

/**
 * Full tests occurrences, may have reference to next occurrences
 */
@XmlRootElement(name = "testOccurrences")
public class TestOccurrences extends TestOccurrencesRef {
    @XmlElement(name = "testOccurrence")
    private List<TestOccurrence> testOccurrences;

    @XmlAttribute private String nextHref;

    public List<TestOccurrence> getTests() {
        return testOccurrences == null ? Collections.emptyList() : testOccurrences;
    }
}