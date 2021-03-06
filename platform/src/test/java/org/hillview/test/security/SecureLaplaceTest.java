package org.hillview.test.security;

import org.hillview.utils.Pair;
import org.hillview.security.SecureLaplace;
import org.hillview.security.TestKeyLoader;
import org.hillview.test.BaseTest;
import org.junit.Test;

public class SecureLaplaceTest extends BaseTest {
    @Test
    public void LaplaceTest() {
        TestKeyLoader tkl = new TestKeyLoader();
        SecureLaplace sl = new SecureLaplace(tkl);
        double scale = 10;
        Pair<Integer, Integer> idx = new Pair<>(10, 11);
        double noise = sl.sampleLaplace(-1, scale, idx);
        if (toPrint)
            System.out.println(noise);
    }
}
