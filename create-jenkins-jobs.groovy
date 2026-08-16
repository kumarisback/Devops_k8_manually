import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl

import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig

import hudson.security.ACL

import jenkins.model.Jenkins

import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition


/*
================================================================================
                    JENKINS JOB CREATOR SCRIPT
================================================================================

WHAT THIS SCRIPT DOES:

1. Creates/updates GitHub credentials:
      ID: github-credentials

2. Creates/updates these Pipeline jobs:

      terraform-pipeline
      frontend-pipeline
      order-service-pipeline
      user-service-pipeline

3. Configures each Pipeline to load its Jenkinsfile directly from GitHub.

4. Uses lightweight checkout so Jenkins only retrieves the Jenkinsfile first.

HOW TO USE:

1. Go to:
      Manage Jenkins
      -> Script Console

2. Replace:
      YOUR_GITHUB_TOKEN_HERE

   with your GitHub Personal Access Token.

3. Click Run.

IMPORTANT:
- Do NOT commit this script with your real GitHub token.
- The GitHub token should have the permissions required to clone the
  repositories.
================================================================================
*/


// =============================================================================
// CONFIGURATION
// =============================================================================

def GITHUB_TOKEN = 'token'

def CREDENTIALS_ID = 'github-credentials'

def GITHUB_USERNAME = 'git'


// =============================================================================
// JOB CONFIGURATION
// =============================================================================

def jobsToCreate = [

    [
        name: 'terraform-pipeline',
        repoUrl: 'https://github.com/kumarisback/terraform.git',
        branch: '*/main',
        scriptPath: 'Jenkinsfile'
    ],

    [
        name: 'frontend-pipeline',
        repoUrl: 'https://github.com/kumarisback/Devops_k8_manually.git',
        branch: '*/main',
        scriptPath: 'frontend/Jenkinsfile'
    ],

    [
        name: 'order-service-pipeline',
        repoUrl: 'https://github.com/kumarisback/Devops_k8_manually.git',
        branch: '*/main',
        scriptPath: 'order-service/Jenkinsfile'
    ],

    [
        name: 'user-service-pipeline',
        repoUrl: 'https://github.com/kumarisback/Devops_k8_manually.git',
        branch: '*/main',
        scriptPath: 'user-service/Jenkinsfile'
    ]
]


// =============================================================================
// JENKINS INITIALIZATION
// =============================================================================

def jenkins = Jenkins.get()

def domain = Domain.global()

def credentialsProvider =
    jenkins.getExtensionList(
        'com.cloudbees.plugins.credentials.SystemCredentialsProvider'
    )[0]

def store = credentialsProvider.getStore()


println ""
println "============================================================"
println "        JENKINS PIPELINE JOB CREATOR"
println "============================================================"
println ""


// =============================================================================
// STEP 1 - CONFIGURE GITHUB CREDENTIALS
// =============================================================================

println "=== Step 1: Configuring GitHub Credentials ==="
println ""

if (GITHUB_TOKEN == 'YOUR_GITHUB_TOKEN_HERE') {

    println "ERROR: GitHub token has not been configured."
    println ""
    println "Please change:"
    println ""
    println "    YOUR_GITHUB_TOKEN_HERE"
    println ""
    println "to your actual GitHub Personal Access Token."
    println ""

    return
}


// -----------------------------------------------------------------------------
// Create new credentials object
// -----------------------------------------------------------------------------

def newCredentials = new UsernamePasswordCredentialsImpl(
    CredentialsScope.GLOBAL,
    CREDENTIALS_ID,
    "GitHub Token Credentials (Automatically Created)",
    GITHUB_USERNAME,
    GITHUB_TOKEN
)


// -----------------------------------------------------------------------------
// Look for existing credentials
// -----------------------------------------------------------------------------

def existingCredentials = CredentialsMatchers.firstOrNull(
    CredentialsProvider.lookupCredentials(
        StandardCredentials.class,
        jenkins,
        ACL.SYSTEM,
        null
    ),
    CredentialsMatchers.withId(CREDENTIALS_ID)
)


// -----------------------------------------------------------------------------
// Update existing credential
// -----------------------------------------------------------------------------

if (existingCredentials != null) {

    println "Found existing credentials:"
    println "ID: ${CREDENTIALS_ID}"
    println ""

    boolean updated = store.updateCredentials(
        domain,
        existingCredentials,
        newCredentials
    )

    if (updated) {

        println "SUCCESS: Updated existing credentials."
        println "Credential ID: ${CREDENTIALS_ID}"

    } else {

        println "INFO: Credentials already up-to-date."
        println "Credential ID: ${CREDENTIALS_ID}"
    }

}


// -----------------------------------------------------------------------------
// Create credential if it doesn't exist
// -----------------------------------------------------------------------------

else {

    println "Credentials not found."
    println "Creating new GitHub credentials..."
    println ""

    boolean added = store.addCredentials(
        domain,
        newCredentials
    )

    if (added) {

        println "SUCCESS: Created GitHub credentials."
        println "Credential ID: ${CREDENTIALS_ID}"

    } else {

        println "ERROR: Failed to create GitHub credentials."

    }
}


println ""
println "=== Step 1 Completed ==="
println ""



// =============================================================================
// STEP 2 - CREATE / UPDATE PIPELINE JOBS
// =============================================================================

println "=== Step 2: Creating / Updating Pipeline Jobs ==="
println ""


jobsToCreate.each { config ->

    println "------------------------------------------------------------"
    println "Processing job: ${config.name}"
    println "Repository: ${config.repoUrl}"
    println "Branch: ${config.branch}"
    println "Jenkinsfile: ${config.scriptPath}"
    println "------------------------------------------------------------"

    try {

        // ---------------------------------------------------------------------
        // Git repository configuration
        // ---------------------------------------------------------------------

        def userRemoteConfig = new UserRemoteConfig(
            config.repoUrl,
            null,
            null,
            CREDENTIALS_ID
        )


        // ---------------------------------------------------------------------
        // Git branch configuration
        // ---------------------------------------------------------------------

        def branchSpec = new BranchSpec(
            config.branch
        )


        // ---------------------------------------------------------------------
        // Git SCM configuration
        // ---------------------------------------------------------------------

        def scm = new GitSCM(
            [userRemoteConfig],
            [branchSpec],
            false,
            [],
            null,
            null,
            []
        )


        // ---------------------------------------------------------------------
        // Pipeline definition
        // ---------------------------------------------------------------------

        def flowDefinition = new CpsScmFlowDefinition(
            scm,
            config.scriptPath
        )


        // ---------------------------------------------------------------------
        // Lightweight checkout
        // ---------------------------------------------------------------------

        flowDefinition.setLightweight(true)


        // ---------------------------------------------------------------------
        // Check whether job already exists
        // ---------------------------------------------------------------------

        def job = jenkins.getItem(config.name)


        // =====================================================================
        // CREATE NEW JOB
        // =====================================================================

        if (job == null) {

            println "Job does not exist."
            println "Creating Pipeline job..."

            job = jenkins.createProject(
                WorkflowJob.class,
                config.name
            )

            job.setDefinition(
                flowDefinition
            )

            job.save()

            println ""
            println "SUCCESS: Created Pipeline job '${config.name}'"
        }


        // =====================================================================
        // UPDATE EXISTING PIPELINE JOB
        // =====================================================================

        else if (job instanceof WorkflowJob) {

            println "Pipeline job already exists."
            println "Updating Pipeline configuration..."

            job.setDefinition(
                flowDefinition
            )

            job.save()

            println ""
            println "SUCCESS: Updated Pipeline job '${config.name}'"
        }


        // =====================================================================
        // EXISTING JOB IS NOT A PIPELINE
        // =====================================================================

        else {

            println ""
            println "WARNING:"
            println "Job '${config.name}' already exists but is not a Pipeline job."
            println "Existing job type: ${job.getClass().getName()}"
            println "Skipping this job."
        }


    } catch (Exception e) {

        println ""
        println "ERROR: Failed to configure job '${config.name}'"
        println "Error: ${e.getMessage()}"
        println ""

        e.printStackTrace()
    }

    println ""
}



// =============================================================================
// FINAL RESULT
// =============================================================================

println ""
println "============================================================"
println "                  EXECUTION COMPLETED"
println "============================================================"
println ""

println "GitHub Credential:"
println "  ${CREDENTIALS_ID}"
println ""

println "Jobs processed:"
jobsToCreate.each { config ->
    println "  - ${config.name}"
}

println ""
println "============================================================"