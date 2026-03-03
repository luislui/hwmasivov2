package com.abricot.hwmasivov2.hwmasivov2;

import java.util.Collection;

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

import com.hp.uca.expert.testmaterial.AlarmListener;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class Hwmasivov2Test2 extends AbstractJunitIntegrationTest {

    private static Logger log = LoggerFactory.getLogger(Hwmasivov2Test2.class);
    private static final String SCENARIO_BEAN_NAME = "hwmasivov2";
    private static final String ALARM_FILE = "src/main/resources/valuepack/hwmasivov2/Alarms2.xml";

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        log.info(Constants.TEST_START.val() + Hwmasivov2Test2.class.getName());
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        log.info(Constants.TEST_END.val() + Hwmasivov2Test2.class.getName()
                + Constants.GROUP_ALT1_SEPARATOR.val());
    }

    // Way to run tests via ANT Junit
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(Hwmasivov2Test2.class);
    }

    @Test
    @DirtiesContext()
    public void test2() throws Exception {
        LogHelper.enter(log, "test2()");

        /*
         * Initialize variables and Enable engine internal logs
         */
        initTest(SCENARIO_BEAN_NAME, BMK_PATH);

        /*
         * Create,Assign and store an Alarm Listener to the current scenario
         */
        AlarmListener allstnr = createAndAssignAlarmListener("1", getScenario());
        setAlarmListener(allstnr);
        /*
         * Send alarms defined in Alarms.xml asynchronously with a tempo of 2
         * seconds between each alarm
         */
        log.info("++++ mandara la alarma test2 ...");
        getProducer().sendAlarmsAsync(ALARM_FILE, 2 * SECOND);
        log.info("++++ mando la alarma test2 ...");

        while (true) {
            /*
            * Wait for an alarm insertion in scenario working memory
            */
            log.info("++++ espera insercion de la alarma test2 ...");
            // waitingForAlarmInsertion(getAlarmListener(), 100 * MS, 30 * SECOND);
            // waitingForAlarmInsertion(allstnr, 100 * MS, 10 * SECOND);
            waitingForAlarmInsertion(allstnr, 100 * MS, 30 * SECOND);
            log.info("++++ detecto insercion de la alarma test2 ...");

            Collection<Alarm> alarms = getAlarmsFromWorkingMemory();
            log.info(String.format("Alarmas en la WM (+1dummy): %d", alarms.size()));
            // assertEquals(5, alarms.size());
            // for (Alarm al : alarms) {
            //     log.info(">> Sitio: {}", al.getCustomFieldValue("Sitio"));
            //     log.info(">> justInserted: {}", al.isJustInserted());
            // }
            Thread.sleep(10000);
        }

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

        // /*
        //  * Wait for an alarm retraction from scenario working memory
        //  */
        // waitingForAlarmRetract(getAlarmListener(), 100 * MS, 100 * SECOND);

        // /*
        //  * Disable Rule Logger to close the file used to compare engine
        //  * historical events
        //  */
        // closeRuleLogFiles(getScenario());

        // /*
        //  * Check test result by comparing the historical engine events with a
        //  * benchmark
        //  */
        // // Checking the log is too detailed
        // // checkTestResult(getLogRuleFileName(), getLogRuleFileNameBmk());

        // LogHelper.exit(log, "test()");
    }
}

