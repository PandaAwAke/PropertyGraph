package com.tinypdg;

import com.tinypdg.graphToDot.SaveCFG;
import com.tinypdg.graphToDot.SavePDG;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

class PropertyGraphTest {

    static File target;

    @BeforeAll
    static void setUpBeforeClass() {
//        target = new File("testcase/MyTest.java");
//        target = new File("testcase/ActiveMQMapMessage.java");
//        target = new File("testcase/PortfolioPublishServlet.java");
//        target = new File("testcase/FilenameGuardFilterNew.java");
//        target = new File("testcase/BaseDataStreamMarshallerNew.java");
        target = new File("testcase/EdgeOperation.java");
    }

    @Test
    void testCFG() {
        SaveCFG.save(target);
    }

    @Test
    void testPDG() {
        SavePDG.save(target);
    }

}