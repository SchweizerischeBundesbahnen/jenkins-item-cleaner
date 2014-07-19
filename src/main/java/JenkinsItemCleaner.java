import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import org.apache.http.client.HttpResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Igor Masen <igor.masen@icloud.com>
 */
public class JenkinsItemCleaner {

    private final static Logger LOGGER = Logger.getLogger(JenkinsItemCleaner.class.getName());

    public static void main(String[] args) {
        if(args.length < 4) {
            usage();
        }
        String jenkinsServerString = args[0];
        String user = args[1];
        String password = args[2];
        int retentionDays = Integer.parseInt(args[3]);
        boolean dryRun = false;

        Date currentDate = new Date(System.currentTimeMillis() * 1000);
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentDate);
        cal.add(Calendar.DATE, -retentionDays);

        if (args.length > 2 && args[4].contains("dryRun")) {
            LOGGER.log(Level.INFO, "dryRun detected, won't delete anything");
            dryRun = true;
        }

        try {
            JenkinsServer jenkinsServer = new JenkinsServer(new URI(jenkinsServerString), user, password);
            Map<String, Job> jobs = jenkinsServer.getJobs();
            LOGGER.log(Level.INFO, "Got " + jobs.size() + " jenkins-jobs");
            Iterator it = jenkinsServer.getJobs().entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry) it.next();
                Job job = (Job) pairs.getValue();
                JobWithDetails jobWithDetails = jobs.get(job.getName()).details();
                BuildWithDetails buildWithDetails;
                Long timestamp;
                if (!jobWithDetails.isBuildable()) {
                    try {
                        buildWithDetails = jobWithDetails.getLastBuild().details();
                        timestamp = buildWithDetails.getTimestamp();
                        Date lastBuildDate = new Date(timestamp * 1000);
                        if (lastBuildDate.before(cal.getTime())) {
                            if (dryRun) {
                                LOGGER.log(Level.INFO, "Would delete " + jobWithDetails.getName());
                            } else {
                                try {
                                    job.getClient().post(job.getUrl() + "doDelete");
                                } catch (HttpResponseException e) {
                                    if (e.getStatusCode() == 302) {
                                        LOGGER.log(Level.INFO, "Deleted " + jobWithDetails.getName());
                                    } else if (e.getStatusCode() == 301) {
                                        LOGGER.log(Level.WARNING,"Could not delete " + job.getName() + " message was " + e.getMessage() + " " + e.getStatusCode() + " maybe you are not allowed to delete?");
                                    } else {
                                        LOGGER.log(Level.WARNING,"Could not delete " + job.getName() + " message was " + e.getMessage() + " " + e.getStatusCode());
                                    }
                                }
                            }
                        }

                    } catch (NullPointerException e) {
                        LOGGER.log(Level.INFO, "Build has never been build " + jobWithDetails.getName());
                    }

                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Server-url doesn't seem to be correct " + jenkinsServerString);
        }

    }

    private static void usage() {
        System.out.println("Example: java -jar jenkins-item-cleaner.jar http://localhost:8080 admin password 180");
        System.out.println("dryRun at the end can be used to test what would happen");
    }

}
