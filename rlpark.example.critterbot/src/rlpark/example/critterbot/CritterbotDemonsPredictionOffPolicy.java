package rlpark.example.critterbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rlpark.plugin.critterbot.actions.CritterbotAction;
import rlpark.plugin.critterbot.actions.XYThetaAction;
import rlpark.plugin.critterbot.crtrlog.CrtrLogFile;
import rlpark.plugin.rltoys.algorithms.functions.policydistributions.helpers.RandomPolicy;
import rlpark.plugin.rltoys.algorithms.functions.states.Projector;
import rlpark.plugin.rltoys.algorithms.predictions.td.GTDLambda;
import rlpark.plugin.rltoys.algorithms.representations.observations.ObsNormalizers;
import rlpark.plugin.rltoys.algorithms.representations.tilescoding.TileCoders;
import rlpark.plugin.rltoys.algorithms.representations.tilescoding.TileCodersHashing;
import rlpark.plugin.rltoys.algorithms.representations.tilescoding.hashing.Hashing;
import rlpark.plugin.rltoys.algorithms.representations.tilescoding.hashing.MurmurHashing;
import rlpark.plugin.rltoys.envio.actions.Action;
import rlpark.plugin.rltoys.envio.observations.Legend;
import rlpark.plugin.rltoys.envio.observations.ObsArray;
import rlpark.plugin.rltoys.envio.policy.SingleActionPolicy;
import rlpark.plugin.rltoys.envio.policy.Policies;
import rlpark.plugin.rltoys.envio.policy.Policy;
import rlpark.plugin.rltoys.horde.Horde;
import rlpark.plugin.rltoys.horde.demons.Demon;
import rlpark.plugin.rltoys.horde.demons.PredictionOffPolicyDemon;
import rlpark.plugin.rltoys.horde.functions.ConstantGamma;
import rlpark.plugin.rltoys.horde.functions.HordeUpdatable;
import rlpark.plugin.rltoys.horde.functions.RewardFunction;
import rlpark.plugin.rltoys.horde.functions.RewardObservationFunction;
import rlpark.plugin.rltoys.math.normalization.IncMeanVarNormalizer;
import rlpark.plugin.rltoys.math.vector.RealVector;
import rlpark.plugin.rltoys.math.vector.SparseVector;
import rlpark.plugin.rltoys.math.vector.implementations.PVector;
import zephyr.plugin.core.api.synchronization.Chrono;
import zephyr.plugin.core.api.synchronization.Clock;

@SuppressWarnings("restriction")
public class CritterbotDemonsPredictionOffPolicy implements Runnable {
  private static final ConstantGamma[] Gammas = new ConstantGamma[] { new ConstantGamma(.8), new ConstantGamma(.99) };
  private final CrtrLogFile robot;
  private final Clock clock = new Clock();
  private final Horde horde;
  private final Random random = new Random(0);
  private final Projector projector;
  private final ObsNormalizers normalizers;
  private final RandomPolicy behaviour;

  public CritterbotDemonsPredictionOffPolicy() {
    robot = new CrtrLogFile("/Users/thomas/Results/daylongs/daylong04.crtrlog.gz");
    normalizers = new ObsNormalizers(robot.legend());
    projector = createRepresentation(robot.legend());
    System.out.println("Creating reward functions...");
    List<RewardFunction> rewardFunctions = createRewardFunctions(robot.legend());
    System.out.println("Creating policies...");
    behaviour = new RandomPolicy(random, XYThetaAction.sevenActions());
    Policy[] policies = createPolicies(XYThetaAction.sevenActions());
    System.out.println("Creating demons...");
    List<Demon> demons = createDemons(behaviour, policies, rewardFunctions);
    horde = new Horde();
    horde.demons().addAll(demons);
    for (RewardFunction rewardFunction : rewardFunctions)
      horde.addBeforeFunction((HordeUpdatable) rewardFunction);
    System.out.println(String.format("Ready for running... %d demons with %d actives features on %d.", demons.size(),
                                     (int) projector.vectorNorm(), projector.vectorSize()));
  }

  private Projector createRepresentation(Legend legend) {
    Hashing hashing = new MurmurHashing(random, 5000);
    TileCoders tileCoders = new TileCodersHashing(hashing, normalizers.getRanges());
    for (int i = 0; i < legend.nbLabels(); i++)
      for (int j = i + 1; j < legend.nbLabels(); j++) {
        tileCoders.addTileCoder(new int[] { i, j }, 10, 10);
        if (tileCoders.vectorNorm() > 400)
          return tileCoders;
      }
    return tileCoders;
  }

  private List<Demon> createDemons(Policy behaviour, Policy[] policies, List<RewardFunction> rewardFunctions) {
    List<Demon> demons = new ArrayList<Demon>();
    for (ConstantGamma gamma : Gammas)
      for (Policy target : policies)
        for (RewardFunction rewardFunction : rewardFunctions) {
          double alpha_v = .1 / projector.vectorNorm();
          double alpha_w = .1 / projector.vectorNorm();
          GTDLambda gtd = new GTDLambda(.3, gamma.gamma(), alpha_v, alpha_w, projector.vectorSize());
          PredictionOffPolicyDemon demon = new PredictionOffPolicyDemon(target, behaviour, gtd, rewardFunction);
          demons.add(demon);
        }
    return demons;
  }

  private Policy[] createPolicies(CritterbotAction[] actions) {
    Policy[] policies = new Policy[actions.length];
    for (int i = 0; i < policies.length; i++)
      policies[i] = new SingleActionPolicy(actions[i]);
    return policies;
  }

  private List<RewardFunction> createRewardFunctions(Legend legend) {
    List<RewardFunction> functions = new ArrayList<RewardFunction>();
    for (String label : robot.legend().getLabels())
      functions.add(new RewardObservationFunction(robot.legend(), label));
    return functions;
  }

  @Override
  public void run() {
    RealVector x_t = null;
    Action a_t = null;
    Chrono chrono = new Chrono();
    long lastTick = 0;
    IncMeanVarNormalizer incMean = new IncMeanVarNormalizer();
    while (clock.tick()) {
      double[] o_tp1 = robot.step();
      double[] no_tp1 = normalizers.update(o_tp1);
      RealVector x_tp1 = projector.project(no_tp1);
      incMean.update(((SparseVector) x_tp1).nonZeroElements());
      x_tp1 = new PVector(x_tp1);
      horde.update(new ObsArray(o_tp1), x_t, a_t, x_tp1);
      Action a_tp1 = Policies.decide(behaviour, x_tp1);
      a_t = a_tp1;
      x_t = x_tp1;
      if (chrono.getCurrentChrono() > 60.0) {
        System.out.println(((clock.timeStep() - lastTick) * 1000.0) / chrono.getCurrentMillis() + " ticks per second. "
            + incMean.mean() + " active features in average");
        chrono.start();
        lastTick = clock.timeStep();
      }
    }
  }

  public static void main(String[] args) {
    new CritterbotDemonsPredictionOffPolicy().run();
  }
}