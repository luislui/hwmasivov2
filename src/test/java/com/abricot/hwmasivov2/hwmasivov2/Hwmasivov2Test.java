package com.abricot.hwmasivov2.hwmasivov2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import junit.framework.JUnit4TestAdapter;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.hp.uca.common.misc.Constants;
import com.hp.uca.common.trace.LogHelper;
import com.hp.uca.expert.alarm.Alarm;
import com.hp.uca.expert.testmaterial.AbstractJunitIntegrationTest;
import com.hp.uca.expert.x733alarm.OperatorState;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class Hwmasivov2Test extends AbstractJunitIntegrationTest {

    private static Logger log = LoggerFactory.getLogger(Hwmasivov2Test.class);
    private static final String SCENARIO_BEAN_NAME = "hwmasivov2";
    private static final String ALARM_FILE = "src/main/resources/valuepack/hwmasivov2/Alarms.xml";

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        log.info(Constants.TEST_START.val() + Hwmasivov2Test.class.getName());
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        log.info(Constants.TEST_END.val() + Hwmasivov2Test.class.getName()
                + Constants.GROUP_ALT1_SEPARATOR.val());
    }

    // Way to run tests via ANT Junit
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(Hwmasivov2Test.class);
    }

    @Test
    @DirtiesContext()
    public void test() throws Exception {
        LogHelper.enter(log, "test()");

        /*
         * Initialize variables and Enable engine internal logs
         */
        initTest(SCENARIO_BEAN_NAME, BMK_PATH);

        /*
         * Create,Assign and store an Alarm Listener to the current scenario
         */
        setAlarmListener(createAndAssignAlarmListener("1", getScenario()));

        /*
         * Send alarms defined in Alarms.xml asynchronously with a tempo of 2
         * seconds between each alarm
         */
        log.info("++++ mandara la alarma ...");
        getProducer().sendAlarmsAsync(ALARM_FILE, 2 * SECOND);
        log.info("++++ mando la alarma ...");

        /*
         * Wait for an alarm insertion in scenario working memory
         */
        log.info("++++ espera insercion de la alarma ...");
        waitingForAlarmInsertion(getAlarmListener(), 100 * MS, 10 * SECOND);
        log.info("++++ detecto insercion de la alarma ...");
        /*
        * Retrieve from Working memory the Alarm with identifier '1'
        */
        log.info("++++ obtiene la alarma 1 ...");
        Alarm alarm = getAlarm("1");
        // log.info(alarm.toFormattedString());

        /*
         * Check that alarm with identifier '1' exists
         */
        log.info("++++ hace el assert ...");
        assertNotNull("The alarm 1 should be present in WM", alarm);
        log.info("++++ hizo el assert ...");

        // /*
        //  * Wait for an alarm update in scenario working memory
        //  */
        // waitingForAlarmUpdate(getAlarmListener(), 100 * MS, 10 * SECOND);
        // /*
        //  * Check the new values of attributes 'problemInformation' &
        //  * 'notificationIdentifier' of alarm '1'
        //  */
        // assertEquals(
        //         "The problemInformation should be New Problem information",
        //         "New Problem information", alarm.getProblemInformation());
        // assertEquals("The notificationIdentifier should be equals to 100",
        //         "100", alarm.getNotificationIdentifier());

        // /*
        //  * Wait for an alarm acknowledgement
        //  */
        // waitingForAlarmAcknowledgement(getAlarmListener(), 100 * MS,
        //         10 * SECOND);
        // /*
        //  * Check if the OperatorState of alarm has been correctly changed to
        //  * ACKNOWLEDGED
        //  */
        // assertEquals("Alarm 1 has been acknowledged",
        //         OperatorState.ACKNOWLEDGED, alarm.getOperatorState());

        /*
         * Wait for an alarm retraction from scenario working memory
         */
        waitingForAlarmRetract(getAlarmListener(), 100 * MS, 10 * SECOND);

        /*
         * Disable Rule Logger to close the file used to compare engine
         * historical events
         */
        closeRuleLogFiles(getScenario());

        /*
         * Check test result by comparing the historical engine events with a
         * benchmark
         */
        // Checking the log is too detailed
        // checkTestResult(getLogRuleFileName(), getLogRuleFileNameBmk());

        LogHelper.exit(log, "test()");
    }
}
