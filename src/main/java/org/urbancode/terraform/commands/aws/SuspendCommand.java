package org.urbancode.terraform.commands.aws;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.urbancode.terraform.commands.common.Command;
import org.urbancode.terraform.commands.common.CommandException;
import org.urbancode.terraform.tasks.aws.ContextAWS;
import org.urbancode.terraform.tasks.aws.EnvironmentTaskAWS;
import org.urbancode.terraform.tasks.aws.InstanceTask;
import org.urbancode.terraform.tasks.aws.helpers.AWSHelper;
import org.urbancode.terraform.tasks.aws.util.InstancePriorityComparator;

import com.amazonaws.services.ec2.AmazonEC2;

public class SuspendCommand implements Command {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    static private final Logger log = Logger.getLogger(SuspendCommand.class);

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    private ContextAWS context;
    private AWSHelper helper;
    private AmazonEC2 client;

    //----------------------------------------------------------------------------------------------
    public SuspendCommand(ContextAWS context) {
        this.context = context;
        this.helper = new AWSHelper();
        this.client = context.fetchEC2Client();
    }


    //----------------------------------------------------------------------------------------------
    /**
     * This will attempt to stop all running instances in the environment.
     */
    @Override
    public void execute()
    throws CommandException {
        List<String> instanceIds = getInstanceIdsInReverseOrder();
        try {
            helper.stopInstances(instanceIds, client);
        } catch (RemoteException e) {
            log.warn("RemoteException while attempting to start instance");
            throw new CommandException(e);
        } catch (InterruptedException e) {
            log.warn("InterruptedException while attempting to start instance");
            throw new CommandException(e);
        }
    }

    //----------------------------------------------------------------------------------------------
    private List<String> getInstanceIdsInReverseOrder() {
        List<String> result = new ArrayList<String>();
        List<InstanceTask> instanceTasks = ((EnvironmentTaskAWS) context.getEnvironment()).getInstances();

        Collections.sort(instanceTasks, new InstancePriorityComparator());
        Collections.reverse(instanceTasks);
        for (InstanceTask instanceTask : instanceTasks) {
            result.add(instanceTask.getId());
        }
        return result;
    }

}
