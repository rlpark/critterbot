package rlpark.plugin.critterview.tests;

import org.junit.Assert;
import org.junit.Test;

import zephyr.plugin.core.ZephyrCore;
import zephyr.plugin.junittesting.RunnableFilesTests;
import zephyr.plugin.junittesting.support.RunnableTests;
import zephyr.plugin.junittesting.support.checklisteners.ControlChecks;

public class CritterviewTests {
  private static String critterbotRoot = "..";

  static public void setCritterbotProjectsRoot(String critterbotRoot) {
    CritterviewTests.critterbotRoot = critterbotRoot;
  }

  @Test(timeout = RunnableFilesTests.TimeOut)
  public void testCritterbotFileLoading() {
    ZephyrCore.setSynchronous(true);
    RunnableTests.testFileLoading(critterbotRoot + "/rlpark.plugin.critterbot/data/wandering.crtrlog", 100);
    Assert.assertEquals(0, ControlChecks.countChildren("zephyr.plugin.critterview.view.observation"));
  }


  @Test
  public void testJythonHordeNextingTest() {
    ZephyrCore.setSynchronous(true);
    RunnableTests.testFileLoading(critterbotRoot + "/rlpark.example.critterbot/pysrc/DemonsPredictionOnPolicy.py", 50);
    Assert.assertEquals(0, ControlChecks.countChildren("zephyr.plugin.critterview.view.observation"));
  }

  @Test
  public void testJythonHordeOffPolicyPredictionsTest() {
    ZephyrCore.setSynchronous(true);
    RunnableTests.testFileLoading(critterbotRoot + "/rlpark.example.critterbot/pysrc/DemonsPredictionOffPolicy.py", 50);
    Assert.assertEquals(0, ControlChecks.countChildren("zephyr.plugin.critterview.view.observation"));
  }

  @Test
  public void testJythonHordeOffPolicyControlTest() {
    ZephyrCore.setSynchronous(true);
    RunnableTests.testFileLoading(critterbotRoot + "/rlpark.example.critterbot/pysrc/DemonsControlOffPolicy.py", 50);
    Assert.assertEquals(0, ControlChecks.countChildren("zephyr.plugin.critterview.view.observation"));
  }
}
