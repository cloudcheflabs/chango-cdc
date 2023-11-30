package co.cloudcheflabs.chango.cdc;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;


public class RunChangoCdcTestRunner {

    @Test
    public void runChangoCdc() throws Exception {
        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-cdc/src/test/resources/configuration-test.yml");

        List<String> args = Arrays.asList(confPath);

        Chango.main(args.toArray(new String[0]));
    }
}
