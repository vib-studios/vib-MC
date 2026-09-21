package net.vibmc.testing;

import net.vibmc.network.packetevents.PacketEventsRuntime;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public final class PacketEventsTestBootstrap implements TestExecutionListener {
    @Override public void testPlanExecutionStarted(TestPlan testPlan){
        try{net.vibmc.mappings.Mappings.load();}
        catch(IllegalStateException error){throw new IllegalStateException(error);}
        PacketEventsRuntime.initialize();
    }
    @Override public void testPlanExecutionFinished(TestPlan testPlan){PacketEventsRuntime.terminate();}
}
