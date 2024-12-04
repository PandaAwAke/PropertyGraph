package com.propertygraph;

import com.propertygraph.graphToDot.SaveCFG;
import com.propertygraph.graphToDot.SavePDG;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

class PropertyGraphTest {

    static File target;

    @BeforeAll
    static void setUpBeforeClass() {
        target = new File("testcase/MyTest.java");
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