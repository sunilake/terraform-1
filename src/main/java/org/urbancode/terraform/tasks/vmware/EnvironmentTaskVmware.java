package org.urbancode.terraform.tasks.vmware;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.urbancode.terraform.tasks.common.Context;
import org.urbancode.terraform.tasks.common.EnvironmentTask;
import org.urbancode.terraform.tasks.common.MultiThreadTask;
import org.urbancode.terraform.tasks.vmware.events.TaskEventService;
import org.urbancode.terraform.tasks.vmware.util.GlobalIpAddressPool;
import org.urbancode.terraform.tasks.vmware.util.IpAddressPool;
import org.urbancode.terraform.tasks.vmware.util.Path;
import org.urbancode.terraform.tasks.vmware.util.VirtualHost;


public class EnvironmentTaskVmware extends EnvironmentTask {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    static private final Logger log = Logger.getLogger(EnvironmentTaskVmware.class);
    static final private int MAX_THREADS = 30;

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    private VirtualHost host;
    private String folderName;
    private IpAddressPool ipPool;

    private Path destPath;
    private Path hostPath;
    private Path datastorePath;

    private FolderTask folderTask;
    private List<CloneTask> cloneTasks = new ArrayList<CloneTask>();
    private List<NetworkTask> networkTasks = new ArrayList<NetworkTask>();
    private List<SecurityGroupTask> securityGroups = new ArrayList<SecurityGroupTask>();

    private TaskEventService eventService;

    //----------------------------------------------------------------------------------------------
    public EnvironmentTaskVmware() {
        this(null);
    }

    //----------------------------------------------------------------------------------------------
    public EnvironmentTaskVmware(Context context) {
        super(context);
        eventService = new TaskEventService();
        ipPool = GlobalIpAddressPool.getInstance().getIpAddressPool();
    }

    //----------------------------------------------------------------------------------------------
    public Context fetchContext() {
        return this.context;
    }

    //----------------------------------------------------------------------------------------------
    public VirtualHost fetchVirtualHost() {
        return host;
    }

    //----------------------------------------------------------------------------------------------
    public Path getHostPath() {
        return hostPath;
    }

    //----------------------------------------------------------------------------------------------
    public Path getDatastorePath() {
        return datastorePath;
    }

    //----------------------------------------------------------------------------------------------
    public Path getDestPath() {
        return destPath;
    }

    //----------------------------------------------------------------------------------------------
    public String getFolderName() {
        return folderName;
    }

    //----------------------------------------------------------------------------------------------
    public FolderTask fetchFolderTask() {
        return this.folderTask;
    }

    //----------------------------------------------------------------------------------------------
    public List<CloneTask> getCloneTasks() {
        return this.cloneTasks;
    }

    //----------------------------------------------------------------------------------------------
    public List<NetworkTask> getNetworkTasks() {
        return this.networkTasks;
    }

    //----------------------------------------------------------------------------------------------
    public List<SecurityGroupTask> getSecurityGroupTasks() {
        return this.securityGroups;
    }

    //----------------------------------------------------------------------------------------------
    public TaskEventService fetchEventService() {
        return this.eventService;
    }

    //----------------------------------------------------------------------------------------------
    public IpAddressPool fetchIpPool() {
        return this.ipPool;
    }

    //----------------------------------------------------------------------------------------------
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    //----------------------------------------------------------------------------------------------
    public void addUUIDToFolderName() {
        String suffix = UUID.randomUUID().toString().replaceAll("-", "");
        folderName = folderName + "-" + suffix.substring(0, 4);
    }

    //----------------------------------------------------------------------------------------------
    public void setDestPath(String destPathString) {
        Path dest = new Path(destPathString);
        this.destPath = dest;
    }

    //----------------------------------------------------------------------------------------------
    public void setHostPath(String hostPathString) {
        Path hostPth = new Path(hostPathString);
        this.hostPath = hostPth;
    }

    //----------------------------------------------------------------------------------------------
    public void setDatastorePath(String datastorePathString) {
        Path datastore = new Path(datastorePathString);
        this.datastorePath = datastore;
    }

    //----------------------------------------------------------------------------------------------
    public void setVirtualHost(VirtualHost host) {
        this.host = host;
    }

    //----------------------------------------------------------------------------------------------
    public void setAllPaths(String datacenter, String hostName, String destination, String datastore) {
        Path datacenterPath = new Path(datacenter);
        hostPath = new Path(datacenterPath, hostName);
        destPath = new Path(datacenterPath, destination);
        datastorePath = new Path(datacenterPath, datastore);
    }

    //----------------------------------------------------------------------------------------------
    public FolderTask createFolder() {
        this.folderTask = new FolderTask();
        this.folderTask.setDestPath(destPath);
        this.folderTask.setVirtualHost(host);
        return this.folderTask;
    }

    //----------------------------------------------------------------------------------------------
    public CloneTask createClone() {
        CloneTask result = new CloneTask(this);
        this.cloneTasks.add(result);
        return result;
    }

    //----------------------------------------------------------------------------------------------
    public NetworkTask createNetwork() {
        NetworkTask result = new NetworkTask();
        result.setHostPath(hostPath);
        result.setVirtualHost(host);
        this.networkTasks.add(result);
        return result;
    }

    //----------------------------------------------------------------------------------------------
    public SecurityGroupTask createSecurityGroup() {
        SecurityGroupTask result = new SecurityGroupTask();
        this.securityGroups.add(result);
        return result;
    }

    //----------------------------------------------------------------------------------------------
    public NetworkTask restoreNetworkForName(String networkName) {
        NetworkTask result = null;

        for (NetworkTask network : networkTasks) {
            if (network.getNetworkName().equals(networkName)) {
                result = network;
                break;
            }
        }

        return result;
    }

    //----------------------------------------------------------------------------------------------
    public SecurityGroupTask restoreSecurityGroupForName(String secGroupName) {
        SecurityGroupTask result = null;

        for (SecurityGroupTask sgt : securityGroups) {
            if (sgt.getName().equals(secGroupName)) {
                result = sgt;
                break;
            }
        }

        return result;
    }

    //----------------------------------------------------------------------------------------------
    @Override
    public void create() {

        if (host == null) {
            throw new NullPointerException("Host is null!");
        }

        //folder task
        createFolder();
        addUUIDToFolderName();
        folderTask.setFolderName(folderName);
        folderTask.create();

        //private network tasks
        for (NetworkTask network : networkTasks) {
            network.setVirtualHost(host);
            network.setHostPath(hostPath);
            network.create();
        }

        //vm clone tasks
        addToCloneTasks();

        try {
            createClonesInOrder();
        } catch (Exception e1) {
            log.warn("exception while creating clones", e1);
        }
        try {
            //wait for new IPs to get updated
            log.info("Sleeping until new IPs are allocated.");
            Thread.sleep(20000);
        } catch (InterruptedException e1) {
            log.warn("interrupted exception while waiting for new IPs", e1);
        }
        for (CloneTask cloneTask : cloneTasks) {
            try {
                cloneTask.setIpListFromVmInfo();
            }
            catch (Exception e) {
                log.warn("exception when setting IP info", e);
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    @Override
    public void destroy() {
        //vm clone tasks
        try {
            //restore folder task and vmware Folder object
            createFolder();
            folderTask.setFolderName(folderName);
            folderTask.restore();

            for (CloneTask cloneTask : cloneTasks) {
                cloneTask.destroy();
            }

            folderTask.destroy();

            for (NetworkTask network : networkTasks) {
                network.setVirtualHost(host);
                network.setHostPath(hostPath);
                network.destroy();
            }
        }
        catch (RemoteException e) {
            log.warn("RemoteException when deleting vm", e);
        }
    }

    //----------------------------------------------------------------------------------------------
    private void addToCloneTasks() {
        List<CloneTask> newCloneList = new ArrayList<CloneTask>();
        for (CloneTask cloneTask : cloneTasks) {
            newCloneList.add(cloneTask);
            int count = cloneTask.fetchServerCount();
            if (count > 1) {
                for (int i=2; i<=count; i++) {
                    try {
                        CloneTask newCloneTask = (CloneTask) cloneTask.clone();
                        newCloneTask.setServerCount(1);
                        newCloneTask.setInstanceName(newCloneTask.getInstanceName() + "-" + i);
                        newCloneList.add(newCloneTask);
                    }
                    catch (CloneNotSupportedException e) {
                        log.warn("clone not supported exception", e);
                    }
                }
            }
        }
        cloneTasks = newCloneList;
    }

    //----------------------------------------------------------------------------------------------
    private void handleCloneCreation(List<CloneTask> cloneTaskList)
    throws Exception {
        long pollInterval = 3000L;
        long timeoutInterval = 10L * 60L * 1000L;
        long start;
        if (cloneTaskList != null && !cloneTaskList.isEmpty()) {
            int threadPoolSize = cloneTaskList.size();
            if (threadPoolSize > MAX_THREADS) {
                threadPoolSize = MAX_THREADS;
            }

            // create instances - launch thread for each one
            List<MultiThreadTask> threadList = new ArrayList<MultiThreadTask>();
            ExecutorService service = Executors.newFixedThreadPool(threadPoolSize);
            start = System.currentTimeMillis();

            for (CloneTask instance : cloneTaskList) {

                MultiThreadTask mThread = new MultiThreadTask(instance, true, context);
                threadList.add(mThread);
                service.execute(mThread);
            }
            service.shutdown(); // accept no more threads

            while (!service.isTerminated()) {
                if (System.currentTimeMillis() - start > timeoutInterval) {
                    throw new RemoteException(
                            "Timeout waiting for creation Instance threads to finish");
                }
                // wait until all threads are done
                Thread.sleep(pollInterval);
            }

            // check for Exceptions caught in threads
            for (MultiThreadTask task : threadList) {
                if (task.getExceptions().size() != 0) {
                    for (Exception e : task.getExceptions()) {
                        log.error("Exception caught!", e);
                        throw e;
                    }
                }
            }
        }
        else {
            log.error("List of instances to launch was null!");
        }
    }

    //----------------------------------------------------------------------------------------------
    private void createClonesInOrder()
    throws Exception {
        PriorityQueue<CloneTask> taskQueue = new PriorityQueue<CloneTask>(cloneTasks);

        List<CloneTask> concurrentClones = new ArrayList<CloneTask>();
        int currentOrder = taskQueue.peek().getOrder();
        //iterate until a different order number is found, then execute the cached clones
        while (!taskQueue.isEmpty()) {
            if (taskQueue.peek().getOrder() == currentOrder) {
                concurrentClones.add(taskQueue.poll());
            }
            else {
                log.info("about to launch " + concurrentClones.size() + " clones");
                handleCloneCreation(concurrentClones);
                log.info("finished creating " + concurrentClones.size() + " clones");
                log.info("There are " + taskQueue.size() + " clones left in the queue");
                concurrentClones.clear();
                CloneTask clone = taskQueue.poll();
                currentOrder = clone.getOrder();
                concurrentClones.add(clone);
            }
        }
        //create final group of clones
        handleCloneCreation(concurrentClones);
    }

    //----------------------------------------------------------------------------------------------
    public String fetchEnvironmentStatus() {
        String result = "";
        String allStatuses = "";
        Set<String> cloneStatuses = new HashSet<String>();
        for (CloneTask cloneTask : cloneTasks) {
            String status = cloneTask.fetchVmStatus();
            cloneStatuses.add(status);
            allStatuses = allStatuses + status + " ";
        }
        int numStatuses = cloneStatuses.size();
        if (cloneStatuses.contains("Shut Down")) {
            result = "Shut Down";
        }
        else if (cloneStatuses.contains("Shutting Down")) {
            result = "Shutting Down";
        }
        else if (cloneStatuses.contains("Starting")) {
            result = "Starting";
        }
        else if (cloneStatuses.contains("Powered Off Or Starting")) {
            result = "Powered Off Or Starting";
        }
        else if (cloneStatuses.contains("Not Started or Powered Off")) {
            result = "Not Started or Powered Off";
        }
        else if (cloneStatuses.contains("Running")) {
            result = "Running";
        }
        log.debug("result status: " + result);
        log.debug("all statuses: " + allStatuses);
        if (result.equals("")) {
            result = "Unknown";
        }
        return result;
    }

}