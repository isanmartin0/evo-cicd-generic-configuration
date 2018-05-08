#!/usr/bin/groovy
import com.evobanco.Utils
import com.evobanco.Constants

def runGenericJenkinsfile() {

    def utils = new com.evobanco.Utils()

    def artifactorySnapshotsURL = 'https://digitalservices.evobanco.com/artifactory/libs-snapshot-local'
    def artifactoryReleasesURL = 'https://digitalservices.evobanco.com/artifactory/libs-release-local'
    def sonarQube = 'http://sonarqube:9000'
    def openshiftURL = 'https://openshift.grupoevo.corp:8443'
    def openshiftCredential = 'openshift'
    def registry = '172.20.253.34'
    def artifactoryCredential = 'artifactory-token'
    def jenkinsNamespace = 'cicd'
    def mavenCmd = 'mvn -U -B -s /opt/evo-maven-settings/evo-maven-settings.xml'
    def mavenProfile = ''
    def springProfile = ''
    def params
    def envLabel
    def branchName
    def branchNameHY
    def branchType
    def artifactoryRepoURL

    //Parallet project configuration (PPC) properties
    def branchPPC = 'master'
    def credentialsIdPPCDefault = '4b18ea85-c50b-40f4-9a81-e89e44e20178' //credentials of the parallel configuration project
    def credentialsIdPPC
    def relativeTargetDirPPC = '/tmp/configs/PPC/'
    def isPPCJenkinsFile = false
    def isPPCJenkinsYaml = false
    def isPPCOpenshiftTemplate = false
    def isPPCApplicationProperties = false
    def isPPCApplicationDevProperties = false
    def isPPCApplicationUatProperties = false
    def isPPCApplicationProdProperties = false
    def jenkinsFilePathPPC = relativeTargetDirPPC + 'Jenkinsfile'
    def jenkinsYamlPathPPC = relativeTargetDirPPC + 'Jenkins.yml'
    def openshiftTemplatePathPPC = relativeTargetDirPPC + 'kube/template.yaml'
    def applicationPropertiesPathPPC = relativeTargetDirPPC + 'configuration_profiles/application.properties'
    def applicationDevPropertiesPathPPC = relativeTargetDirPPC + 'configuration_profiles/dev/application-dev.properties'
    def applicationUatPropertiesPathPPC = relativeTargetDirPPC + 'configuration_profiles/uat/application-uat.properties'
    def applicationProdPropertiesPathPPC = relativeTargetDirPPC + 'configuration_profiles/prod/application-prod.properties'
    def configMapsVolumePersistDefaultPath = '/usr/local/tomcat/conf'
    def jenknsFilePipelinePPC

    //Generic project configuration properties
    def gitDefaultProjectConfigurationPath='https://github.com/isanmartin0/evo-cicd-generic-configuration'
    def relativeTargetDirGenericPGC = '/tmp/configs/generic/'
    def branchGenericPGC = 'master'
    def credentialsIdGenericPGC = '4b18ea85-c50b-40f4-9a81-e89e44e20178' //credentials of the generic configuration project
    def jenkinsYamlGenericPath = relativeTargetDirGenericPGC + 'Jenkins.yml'
    def openshiftTemplateGenericPath = relativeTargetDirGenericPGC + 'kube/template.yaml'
    def isGenericJenkinsYaml = false

    def pom
    def projectURL
    def artifactId
    def groupId

    int maxOldBuildsToKeep = 0
    int daysOldBuildsToKeep = 0

    //Taurus parameters
    def taurus_test_base_path = 'src/test/taurus'
    def acceptance_test_path = '/acceptance_test/'
    def performance_test_path = '/performance_test/'
    def smoke_test_path = '/smoke_test/'
    def security_test_path = '/security_test/'


    def openshift_route_hostname = ''
    def openshift_route_hostname_with_protocol = ''

    //AppDynamics parameters
    Boolean creationAppDynamicsConfigMap = false
    def isPPCAppDynamicsTemplate = false
    def appDynamicsTemplatePath = relativeTargetDirGenericPGC + 'appDynamics/appDynamics_template.yaml'
    def appDynamicsTemplatePathPPC = relativeTargetDirPPC + 'appDynamics/appDynamics_template.yaml'
    def appDynamicsConfigMapsVolumePersistDefaultPath = '/opt/appdynamics/conf'

    echo "BEGIN GENERIC CONFIGURATION PROJECT (PGC)"

    node('maven') {

        //sleep 10
        checkout scm


        try {
            def credentialsIdPPCArray = scm.userRemoteConfigs.credentialsId
            credentialsIdPPC = credentialsIdPPCArray.first()
            echo "Using credentialsIdPPCDefault value for access to Parallel Project Configuration (PPC)"

        } catch (exc) {
            echo 'There is an error on retrieving credentialsId of multibranch configuration'
            def exc_message = exc.message
            echo "${exc_message}"

            credentialsIdPPC = credentialsIdPPCDefault
        }

        echo "credentialsIdPPC: ${credentialsIdPPC}"

        stage('Detect Parallel project configuration (PPC)') {

            pom = readMavenPom()
            projectURL = pom.url
            artifactId = pom.artifactId
            groupId = utils.getProjectGroupId(pom.groupId, pom.parent.groupId, false)

            try {
                def parallelConfigurationProject = utils.getParallelConfigurationProjectURL(projectURL, artifactId)

                echo "Parallel configuration project ${parallelConfigurationProject} searching"

                retry (3)
                        {
                            checkout([$class                           : 'GitSCM',
                                      branches                         : [[name: branchPPC]],
                                      doGenerateSubmoduleConfigurations: false,
                                      extensions                       : [[$class           : 'RelativeTargetDirectory',
                                                                           relativeTargetDir: relativeTargetDirPPC]],
                                      submoduleCfg                     : [],
                                      userRemoteConfigs                : [[credentialsId: credentialsIdPPC,
                                                                           url          : parallelConfigurationProject]]])
                        }
                echo "Parallel configuration project ${parallelConfigurationProject} exits"

                // Jenkinsfile
                isPPCJenkinsFile = fileExists jenkinsFilePathPPC

                if (isPPCJenkinsFile) {
                    echo "Parallel configuration project Jenkinsfile... FOUND"
                } else {
                    echo "Parallel configuration project Jenkinsfile... NOT FOUND"
                }


                // Jenkins.yml
                isPPCJenkinsYaml = fileExists jenkinsYamlPathPPC

                if (isPPCJenkinsYaml) {
                    echo "Parallel configuration project Jenkins.yml... FOUND"
                } else {
                    echo "Parallel configuration project Jenkins.yml... NOT FOUND"
                }

                // Openshift template (template.yaml)
                isPPCOpenshiftTemplate = fileExists openshiftTemplatePathPPC

                if (isPPCOpenshiftTemplate) {
                    echo "Parallel configuration project Openshift template... FOUND"
                } else {
                    echo "Parallel configuration project Openshift template... NOT FOUND"
                }

                //application.properties
                isPPCApplicationProperties = fileExists applicationPropertiesPathPPC

                if (isPPCApplicationProperties) {
                    echo "Parallel configuration project profile application.properties... FOUND"
                } else {
                    echo "Parallel configuration project profile application.properties... NOT FOUND"
                }

                //application-dev.properties
                isPPCApplicationDevProperties = fileExists applicationDevPropertiesPathPPC

                if (isPPCApplicationDevProperties) {
                    echo "Parallel configuration project profile application-dev.properties... FOUND"
                } else {
                    echo "Parallel configuration project profile application-dev.properties... NOT FOUND"
                }

                //application-uat.properties
                isPPCApplicationUatProperties = fileExists applicationUatPropertiesPathPPC

                if (isPPCApplicationUatProperties) {
                    echo "Parallel configuration project profile application-uat.properties... FOUND"
                } else {
                    echo "Parallel configuration project profile application-uat.properties... NOT FOUND"
                }


                //application-prod.properties
                isPPCApplicationProdProperties = fileExists applicationProdPropertiesPathPPC

                if (isPPCApplicationProdProperties) {
                    echo "Parallel configuration project profile application-prod.properties... FOUND"
                } else {
                    echo "Parallel configuration project profile application-prod.properties... NOT FOUND"
                }


                //appDynamics template
                isPPCAppDynamicsTemplate = fileExists appDynamicsTemplatePathPPC

                if (isPPCAppDynamicsTemplate) {
                    echo "Parallel configuration project AppDynamics template... FOUND"
                } else {
                    echo "Parallel configuration project AppDynamics template... NOT FOUND"
                }


                echo "isPPCJenkinsFile : ${isPPCJenkinsFile}"
                echo "isPPCJenkinsYaml : ${isPPCJenkinsYaml}"
                echo "isPPCOpenshiftTemplate : ${isPPCOpenshiftTemplate}"
                echo "isPPCApplicationProperties : ${isPPCApplicationProperties}"
                echo "isPPCApplicationDevProperties : ${isPPCApplicationDevProperties}"
                echo "isPPCApplicationUatProperties : ${isPPCApplicationUatProperties}"
                echo "isPPCApplicationProdProperties : ${isPPCApplicationProdProperties}"
                echo "isPPCAppDynamicsTemplate : ${isPPCAppDynamicsTemplate}"

            }
            catch (exc) {
                echo 'There is an error on retrieving parallel project configuration'
                def exc_message = exc.message
                echo "${exc_message}"
            }
        }



        if (isPPCJenkinsFile) {

            stage('Switch to parallel configuration project Jenkinsfile') {

                echo "Loading Jenkinsfile from Parallel Configuration Project (PPC)"

                jenknsFilePipelinePPC = load jenkinsFilePathPPC

                echo "Jenkinsfile from Parallel Configuration Project (PPC) loaded"

                echo "Executing Jenkinsfile from Parallel Configuration Project (PPC)"

                jenknsFilePipelinePPC.runPPCJenkinsfile()
            }


        } else {
            echo "Executing Jenkinsfile from Generic Configuration Project (PGC)"

            stage('Load pipeline configuration') {

                if (isPPCJenkinsYaml && isPPCOpenshiftTemplate && isPPCAppDynamicsTemplate) {
                    //The generic pipeline will use Jenkins.yml, Openshift template and AppDynamics template of the parallel project configuration

                    //Take parameters of the parallel project configuration (PPC)
                    params = readYaml  file: jenkinsYamlPathPPC
                    echo "Using Jenkins.yml from parallel project configuration (PPC)"

                    //The template is provided by parallel project configuration (PPC)
                    params.openshift.templatePath = relativeTargetDirPPC + params.openshift.templatePath
                    echo "Template provided by parallel project configuration (PPC)"

                    assert params.openshift.templatePath?.trim()

                    echo "params.openshift.templatePath: ${params.openshift.templatePath}"

                    //The AppDynamics template is provided by parallel project configuration (PPC)
                    params.appDynamics.appDynamicsTemplatePath = relativeTargetDirPPC + params.appDynamics.appDynamicsTemplatePath
                    echo "AppDynamics template provided by parallel project configuration (PPC)"

                    assert params.appDynamics.appDynamicsTemplatePath?.trim()

                    echo "params.appDynamics.appDynamicsTemplatePath: ${params.appDynamics.appDynamicsTemplatePath}"

                } else {
                    //The generic pipeline will use generic Jenkins.yml or generic Openshift template
                    //We need load this elements

                    echo "Generic configuration project loading"

                    retry (3) {
                        checkout([$class                           : 'GitSCM',
                                  branches                         : [[name: branchGenericPGC]],
                                  doGenerateSubmoduleConfigurations: false,
                                  extensions                       : [[$class           : 'RelativeTargetDirectory',
                                                                       relativeTargetDir: relativeTargetDirGenericPGC]],
                                  submoduleCfg                     : [],
                                  userRemoteConfigs                : [[credentialsId: credentialsIdGenericPGC,
                                                                       url          : gitDefaultProjectConfigurationPath]]])
                    }

                    echo "Generic configuration project loaded"


                    if (isPPCJenkinsYaml) {
                        //Take parameters of the parallel project configuration (PPC)
                        params = readYaml  file: jenkinsYamlPathPPC
                        echo "Using Jenkins.yml from parallel project configuration (PPC)"
                    } else {
                        //Take the generic parameters
                        params = readYaml  file: jenkinsYamlGenericPath
                        echo "Using Jenkins.yml from generic project"
                    }

                    if (isPPCOpenshiftTemplate) {
                        //The template is provided by parallel project configuration (PPC)
                        params.openshift.templatePath = relativeTargetDirPPC + params.openshift.templatePath
                        echo "Template provided by parallel project configuration (PPC)"
                    } else {
                        //The tamplate is provided by generic configuration
                        params.openshift.templatePath = relativeTargetDirGenericPGC + params.openshift.templatePath
                        echo "Template provided by generic configuration project"
                    }

                    assert params.openshift.templatePath?.trim()

                    echo "params.openshift.templatePath: ${params.openshift.templatePath}"

                    if (isPPCAppDynamicsTemplate) {
                        //The AppDynamics template is provided by parallel project configuration (PPC)
                        params.appDynamics.appDynamicsTemplatePath = relativeTargetDirPPC + params.appDynamics.appDynamicsTemplatePath
                        echo "AppDynamics template provided by parallel project configuration (PPC)"
                    } else {
                        //The tamplate is provided by generic configuration
                        params.appDynamics.appDynamicsTemplatePath = relativeTargetDirGenericPGC + params.appDynamics.appDynamicsTemplatePath
                        echo "Appdynamics template provided by generic configuration project"
                    }

                    assert params.appDynamics.appDynamicsTemplatePath?.trim()

                    echo "params.appDynamics.appDynamicsTemplatePath: ${params.appDynamics.appDynamicsTemplatePath}"
                }

            }


            stage('Prepare') {
                echo "Prepare stage (PGC)"

                setDisplayName()

                echo "${currentBuild.displayName}"

                branchName = utils.getBranch()
                echo "We are on branch ${branchName}"
                branchType = utils.getBranchType(branchName)
                echo "This branch is a ${branchType} branch"
                branchNameHY = branchName.replace("/", "-").replace(".", "-").replace("_","-")
                echo "Branch name processed: ${branchName}"

                artifactoryRepoURL = (branchType == 'master' || branchType == 'release' || branchType == 'hotfix')  ? artifactoryReleasesURL : artifactorySnapshotsURL

                def isValidVersion = utils.isValidBranchPomVersion(pom.version, branchType)

                if (!isValidVersion) {
                    //Sufix -SNAPSHOT is required for develop and feature branch types and is forbidden for release,hotfix and master branch types
                    currentBuild.result = Constants.FAILURE_BUILD_RESULT
                    throw new hudson.AbortException('Version of artifact in pom is not allowed for this type of branch')
                }

            }

            stage ('Prepare profiles') {
                switch (branchType) {
                    case 'feature':
                        echo "Detect feature type branch"
                        envLabel="dev"
                        if (params.maven.profileFeature) {
                            mavenProfile = "-P${params.maven.profileFeature}"
                        }
                        if (params.spring.profileFeature) {
                            springProfile = params.spring.profileFeature
                        }
                        break
                    case 'develop':
                        echo "Detect develop type branch"
                        envLabel="dev"
                        if (params.maven.profileDevelop) {
                            mavenProfile = "-P${params.maven.profileDevelop}"
                        }
                        if (params.spring.profileDevelop) {
                            springProfile = params.spring.profileDevelop
                        }
                        break
                    case 'release':
                        echo "Detect release type branch"
                        envLabel="uat"
                        if (params.maven.profileRelease) {
                            mavenProfile = "-P${params.maven.profileRelease}"
                        }
                        if (params.spring.profileRelease) {
                            springProfile = params.spring.profileRelease
                        }
                        break
                    case 'master':
                        echo "Detect master type branch"
                        envLabel="pro"
                        if (params.maven.profileMaster) {
                            mavenProfile = "-P${params.maven.profileMaster}"
                        }
                        if (params.spring.profileMaster) {
                            springProfile = params.spring.profileMaster
                        }
                        break
                    case 'hotfix':
                        echo "Detect hotfix type branch"
                        envLabel="uat"
                        if (params.maven.profileHotfix) {
                            mavenProfile = "-P${params.maven.profileHotfix}"
                        }
                        if (params.spring.profileHotfix) {
                            springProfile = params.spring.profileHotfix
                        }
                        break
                }

                echo "Maven profile selected: ${mavenProfile}"
                echo "Spring profile selected: ${springProfile}"
            }


            if (branchName != 'master')
            {
                if (branchType in params.testing.predeploy.checkstyle) {
                    stage('Checkstyle') {
                        echo "Running Checkstyle artifact..."
                        sh "${mavenCmd} checkstyle:check -DskipTests=true ${mavenProfile}"
                    }
                } else {
                    echo "Skipping Checkstyle..."
                }

                stage('Build') {
                    echo "Building artifact..."
                    sh "${mavenCmd} package -DskipTests=true -Dcheckstyle.skip=true ${mavenProfile}"
                }

                if (branchType in params.testing.predeploy.unitTesting) {
                    stage('Unit Tests') {
                        echo "Running unit tests..."
                        sh "${mavenCmd} verify -Dcheckstyle.skip=true ${mavenProfile}"
                    }
                } else {
                    echo "Skipping unit tests..."
                }

                if (branchType in params.testing.predeploy.sonarQube) {
                    stage('SonarQube') {
                        echo "Running SonarQube..."

                        def sonar_project_key = groupId + ":" + artifactId + "-" + branchNameHY
                        def sonar_project_name = artifactId + "-" + branchNameHY

                        echo "sonar_project_key: ${sonar_project_key}"
                        echo "sonar_project_name: ${sonar_project_name}"

                        sh "${mavenCmd} sonar:sonar -Dsonar.host.url=${sonarQube} ${mavenProfile} -Dsonar.projectKey=${sonar_project_key} -Dsonar.projectName=${sonar_project_name}"

                    }
                } else {
                    echo "Skipping Running SonarQube..."
                }

                stage('Artifact Deploy') {
                    echo "Deploying artifact to Artifactory..."
                    sh "${mavenCmd} deploy -DskipTests=true -Dcheckstyle.skip=true ${mavenProfile}"
                }
            } else {
                // Is the master branch. Check the existence of artifact on Artifactory

                stage('Check release version on Artifactory') {
                    def artifactoryResponseCode = checkArtifactoryReleaseVersion {
                        artCredential = artifactoryCredential
                        repoUrl = artifactoryRepoURL
                    }

                    echo "Artifactory response status code: ${artifactoryResponseCode}"

                    if (artifactoryResponseCode != null && Constants.HTTP_STATUS_CODE_OK.equals(artifactoryResponseCode)) {
                        echo "Artifact is avalaible for the pipeline on Artifactory"
                    } else {
                        currentBuild.result = Constants.FAILURE_BUILD_RESULT
                        throw new hudson.AbortException('The artifact on Artifactory is not avalaible for the pipeline')
                    }

                }

            }

            /**************************************************************************
             ************* APPLICATION CONFIG MAP CREATION PARAMETERS *****************
             **************************************************************************/

            //Parameters for creation Config Maps
            Boolean useConfigurationProfilesFiles = false
            Boolean persistConfigurationProfilesFiles = false
            def configMapsVolumePersistPath = ''
            echo "params.spring.useConfigurationProfilesFiles: ${params.spring.useConfigurationProfilesFiles}"
            echo "params.spring.persistConfigurationProfilesFiles: ${params.spring.persistConfigurationProfilesFiles}"
            echo "params.spring.configMapsVolumePersistPath: ${params.spring.configMapsVolumePersistPath}"

            if (params.spring.useConfigurationProfilesFiles) {
                useConfigurationProfilesFiles = params.spring.useConfigurationProfilesFiles.toBoolean()
            }

            if (useConfigurationProfilesFiles) {
                if (params.spring.persistConfigurationProfilesFiles) {
                    persistConfigurationProfilesFiles = params.spring.persistConfigurationProfilesFiles.toBoolean()
                }

                if (persistConfigurationProfilesFiles) {
                    if (params.spring.configMapsVolumePersistPath) {
                        configMapsVolumePersistPath = params.spring.configMapsVolumePersistPath
                    } else {
                        configMapsVolumePersistPath = configMapsVolumePersistDefaultPath
                    }
                }
            }

            echo "useConfigurationProfilesFiles value: ${useConfigurationProfilesFiles}"
            echo "persistConfigurationProfilesFiles value: ${persistConfigurationProfilesFiles}"
            echo "configMapsVolumePersistPath value: ${configMapsVolumePersistPath}"


            stage('OpenShift Build') {

                /**********************************************************
                 ************* OPENSHIFT PROJECT CREATION *****************
                 **********************************************************/

                echo "Building image on OpenShift..."

                openshiftCheckAndCreateProject {
                    oseCredential = openshiftCredential
                    cloudURL = openshiftURL
                    environment = envLabel
                    jenkinsNS = jenkinsNamespace
                    artCredential = artifactoryCredential
                    template = params.openshift.templatePath
                    branchHY = branchNameHY
                    branch_type = branchType
                    dockerRegistry = registry
                }

                /***************************************************************
                 ************* APPLICATION CONFIG MAP CREATION *****************
                 ***************************************************************/

                boolean configMapPersisted = false

                if (useConfigurationProfilesFiles) {
                    def configMapCreated = openshiftConfigMapsCreation {
                        springProfileActive = springProfile
                        isPPCApplicationPropertiesOpenshift =  isPPCApplicationProperties
                        isPPCApplicationDevPropertiesOpenshift = isPPCApplicationDevProperties
                        isPPCApplicationUatPropertiesOpenshift = isPPCApplicationUatProperties
                        isPPCApplicationProdPropertiesOpenshift = isPPCApplicationProdProperties
                        applicationPropertiesPathPPCOpenshift = applicationPropertiesPathPPC
                        applicationDevPropertiesPathPPCOpenshift = applicationDevPropertiesPathPPC
                        applicationUatPropertiesPathPPCOpenshift = applicationUatPropertiesPathPPC
                        applicationProdPropertiesPathPPCOpenshift = applicationProdPropertiesPathPPC
                        branchHY = branchNameHY
                        branch_type = branchType
                    }

                    if (configMapCreated && persistConfigurationProfilesFiles) {
                        configMapPersisted = openshiftConfigMapsPersistence {
                            configMapsVolumePersistPathOpenshift = configMapsVolumePersistPath
                            branchHY = branchNameHY
                            branch_type = branchType
                        }
                    }

                }

                /***************************************************************
                 ************* APPDYNAMICS CONFIG MAP CREATION *****************
                 ***************************************************************/

                boolean appDynamicsConfigMapCreated = false
                boolean appDynamicsConfigMapPersisted = false

                echo "params.appDynamics.creationAppDynamicsConfigMap: ${params.appDynamics.creationAppDynamicsConfigMap}"

                if (params.appDynamics.creationAppDynamicsConfigMap) {
                    creationAppDynamicsConfigMap = params.appDynamics.creationAppDynamicsConfigMap.toBoolean()
                }

                echo "creationAppDynamicsConfigMap value: ${creationAppDynamicsConfigMap}"

                if (creationAppDynamicsConfigMap && (branchType == 'release' || branchType == 'hotfix' || branchType == 'master') && (branchType in params.appDynamics.branch)) {
                    //Only the release,hotfix and master branch types can have Appdynamics agent (if the pipeline configuration parameters allow it)

                    def appDynamicsConfigMapsVolumePersistPath = ''
                    echo "params.appDynamics.appDynamicsConfigMapsVolumePersistPath: ${params.appDynamics.appDynamicsConfigMapsVolumePersistPath}"

                    if (params.appDynamics.appDynamicsConfigMapsVolumePersistPath) {
                        appDynamicsConfigMapsVolumePersistPath = params.appDynamics.appDynamicsConfigMapsVolumePersistPath
                    } else {
                        appDynamicsConfigMapsVolumePersistPath = appDynamicsConfigMapsVolumePersistDefaultPath
                    }

                    echo "params.appDynamics.appDynamicsTemplatePath: ${params.appDynamics.appDynamicsTemplatePath}"
                    echo "appDynamicsConfigMapsVolumePersistPath value: ${appDynamicsConfigMapsVolumePersistPath}"
                    echo "params.appDynamics.javaOpts: ${params.appDynamics.javaOpts}"

                    if (branchType == 'release' || branchType == 'hotfix') {


                        echo "params.appDynamics.controllerHostnameUAT: ${params.appDynamics.controllerHostnameUAT}"
                        echo "params.appDynamics.controllerPortUAT: ${params.appDynamics.controllerPortUAT}"
                        echo "params.appDynamics.controllerSSLEnabledUAT: ${params.appDynamics.controllerSSLEnabledUAT}"
                        echo "params.appDynamics.agentApplicationNamePrefix: ${params.appDynamics.agentApplicationNamePrefix}"
                        echo "params.appDynamics.agentApplicationNameSufix: ${params.appDynamics.agentApplicationNameSufix}"
                        echo "params.appDynamics.agentTierNamePrefix: ${params.appDynamics.agentTierNamePrefix}"
                        echo "params.appDynamics.agentTierNameSufix: ${params.appDynamics.agentTierNameSufix}"
                        echo "params.appDynamics.agentAccountName: ${params.appDynamics.agentAccountName}"
                        echo "params.appDynamics.agentAccountAccessKeyUAT: ${params.appDynamics.agentAccountAccessKeyUAT}"

                        Boolean controllerSSLEnabled = false

                        if (params.appDynamics.controllerSSLEnabledUAT) {
                            controllerSSLEnabled = params.appDynamics.controllerSSLEnabledUAT.toBoolean()
                        }

                        //Detect existence and show parameters of agent for UAT environment
                        if (!params.appDynamics.controllerHostnameUAT || !params.appDynamics.controllerPortUAT ||
                                !params.appDynamics.agentAccountName || !params.appDynamics.agentAccountAccessKeyUAT || !params.appDynamics.appDynamicsTemplatePath) {
                            currentBuild.result = Constants.FAILURE_BUILD_RESULT
                            throw new hudson.AbortException('There are mandatory AppDynamics parameters without value for UAT environment. The mandatory parameters are: controllerHostnameUAT, controllerPortUAT, agentAccountNameUAT, agentAccountAccessKeyUAT and appDynamicsTemplatePath')
                        }

                        appDynamicsConfigMapCreated = openshiftAppDynamicsConfigMapsCreation {
                            appDynamicsTemplate = params.appDynamics.appDynamicsTemplatePath
                            appDynamics_controller_hostname = params.appDynamics.controllerHostnameUAT
                            appDynamics_controller_port = params.appDynamics.controllerPortUAT
                            appDynamics_controller_ssl_enabled = controllerSSLEnabled
                            appDynamics_agent_application_name_prefix = params.appDynamics.agentApplicationNamePrefix
                            appDynamics_agent_application_name_sufix = params.appDynamics.agentApplicationNameSufix
                            appDynamics_agent_tier_name_prefix = params.appDynamics.agentTierNamePrefix
                            appDynamics_agent_tier_name_sufix = params.appDynamics.agentTierNameSufix
                            appDynamics_agent_account_name = params.appDynamics.agentAccountName
                            appDynamics_agent_account_access_key = params.appDynamics.agentAccountAccessKeyUAT
                            branchHY = branchNameHY
                            branch_type = branchType
                        }

                    } else if (branchType == 'master') {

                        echo "params.appDynamics.controllerHostnamePRO: ${params.appDynamics.controllerHostnamePRO}"
                        echo "params.appDynamics.controllerPortPRO: ${params.appDynamics.controllerPortPRO}"
                        echo "params.appDynamics.controllerSSLEnabledPRO: ${params.appDynamics.controllerSSLEnabledPRO}"
                        echo "params.appDynamics.agentApplicationNamePrefix: ${params.appDynamics.agentApplicationNamePrefix}"
                        echo "params.appDynamics.agentApplicationNameSufix: ${params.appDynamics.agentApplicationNameSufix}"
                        echo "params.appDynamics.agentTierNamePrefix: ${params.appDynamics.agentTierNamePrefix}"
                        echo "params.appDynamics.agentTierNameSufix: ${params.appDynamics.agentTierNameSufix}"
                        echo "params.appDynamics.agentAccountName: ${params.appDynamics.agentAccountName}"
                        echo "params.appDynamics.agentAccountAccessKeyPRO: ${params.appDynamics.agentAccountAccessKeyPRO}"

                        Boolean controllerSSLEnabled = false

                        if (params.appDynamics.controllerSSLEnabledPRO) {
                            controllerSSLEnabled = params.appDynamics.controllerSSLEnabledPRO.toBoolean()
                        }

                        //Detect existence and show parameters of agent for PRO environment
                        if (!params.appDynamics.controllerHostnamePRO || !params.appDynamics.controllerPortPRO ||
                                !params.appDynamics.agentAccountName || !params.appDynamics.agentAccountAccessKeyPRO || !params.appDynamics.appDynamicsTemplatePath) {
                            currentBuild.result = Constants.FAILURE_BUILD_RESULT
                            throw new hudson.AbortException('There are mandatory AppDynamics parameters without value for PRO environment. The mandatory parameters are: controllerHostnamePRO, controllerPortPRO, agentAccountNamePRO, agentAccountAccessKeyPRO and appDynamicsTemplatePath')
                        }


                        appDynamicsConfigMapCreated = openshiftAppDynamicsConfigMapsCreation {
                            appDynamicsTemplate = params.appDynamics.appDynamicsTemplatePath
                            appDynamics_controller_hostname = params.appDynamics.controllerHostnamePRO
                            appDynamics_controller_port = params.appDynamics.controllerPortPRO
                            appDynamics_controller_ssl_enabled = controllerSSLEnabled
                            appDynamics_agent_application_name_prefix = params.appDynamics.agentApplicationNamePrefix
                            appDynamics_agent_application_name_sufix = params.appDynamics.agentApplicationNameSufix
                            appDynamics_agent_tier_name_prefix = params.appDynamics.agentTierNamePrefix
                            appDynamics_agent_tier_name_sufix = params.appDynamics.agentTierNameSufix
                            appDynamics_agent_account_name = params.appDynamics.agentAccountName
                            appDynamics_agent_account_access_key = params.appDynamics.agentAccountAccessKeyPRO
                            branchHY = branchNameHY
                            branch_type = branchType
                        }

                    }

                    //Persistence of the Appdynamics config map created
                    if (appDynamicsConfigMapCreated) {

                        echo "The AppDynamics config map has been created"
                        appDynamicsConfigMapPersisted = openshiftAppDynamicsConfigMapsPersistence {
                            appDynamicsConfigMapsVolumePersistPathOpenshift = appDynamicsConfigMapsVolumePersistPath
                            branchHY = branchNameHY
                            branch_type = branchType
                        }

                        if (appDynamicsConfigMapPersisted) {
                            echo "The persistence of AppDynamics config map has been done in ${appDynamicsConfigMapsVolumePersistPath}"
                        } else {
                            echo "WARNING. The AppDynamics config map hasn't been persisted. Maybe there was a prevously persistence of the config map"
                        }

                    } else {
                        currentBuild.result = Constants.FAILURE_BUILD_RESULT
                        throw new hudson.AbortException('There is a problem with the creation of the AppDynamics config map')
                    }

                }


                /**************************************************************
                 ************* ENVIRONMENT VARIABLES CREATION *****************
                 **************************************************************/

                retry(3) {
                    openshiftEnvironmentVariables {
                        springProfileActive = springProfile
                        branchHY = branchNameHY
                        branch_type = branchType
                        configMapPersistedOpenshift = configMapPersisted
                        configMapsVolumePersistPathOpenshift = configMapsVolumePersistPath
                        appDynamicsConfigMapCreatedOpenshift = appDynamicsConfigMapCreated
                        appDynamicsJavaOpts = params.appDynamics.javaOpts
                        appDynamicsAgentReuseNodeNamePrefix =  params.appDynamics.agentReuseNodeNamePrefix
                        appDynamicsAgentReuseNodeNameSufix =  params.appDynamics.agentReuseNodeNameSufix
                    }

                    sleep(10)
                }

                openshiftBuildProject {
                    artCredential = artifactoryCredential
                    snapshotRepoUrl = artifactorySnapshotsURL
                    repoUrl = artifactoryRepoURL
                    javaOpts = ''
                    springProfileActive = springProfile
                    bc = params.openshift.buildConfigName
                    is = params.openshift.imageStreamName
                    branchHY = branchNameHY
                    branch_type = branchType
                }


            }
        }

    } // end of node


    if (!isPPCJenkinsFile) {
        def deploy = 'Yes'

        if (branchType in params.confirmDeploy) {
            try {
                stage('Decide on Deploying') {

                    //Parameters timeout deploy answer

                    Boolean timeoutConfirmDeploy = false
                    int timeoutConfirmDeployTime = 0
                    String timeoutConfirmDeployUnit = ''
                    boolean isTimeoutConfirmDeployUnitValid = false

                    echo "params.timeoutConfirmDeploy: ${params.timeoutConfirmDeploy}"

                    if (params.timeoutConfirmDeploy != null) {
                        timeoutConfirmDeploy = params.timeoutConfirmDeploy.toBoolean()
                    }

                    if (timeoutConfirmDeploy) {
                        echo "params.timeoutConfirmDeployTime: ${params.timeoutConfirmDeployTime}"
                        echo "params.timeoutConfirmDeployUnit: ${params.timeoutConfirmDeployUnit}"

                        String timeoutConfirmDeployTimeParam = params.timeoutConfirmDeployTime
                        if (timeoutConfirmDeployTimeParam != null && timeoutConfirmDeployTimeParam.isInteger()) {
                            timeoutConfirmDeployTime = timeoutConfirmDeployTimeParam as Integer
                        }

                        if (params.timeoutConfirmDeployUnit != null && ("NANOSECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MICROSECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MILLISECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "SECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MINUTES".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "HOURS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "DAYS".equals(params.timeoutConfirmDeployUnit.toUpperCase()))) {
                            isTimeoutConfirmDeployUnitValid = true
                            timeoutConfirmDeployUnit = params.timeoutConfirmDeployUnit.toUpperCase()
                        }
                    }

                    echo "timeoutConfirmDeploy value: ${timeoutConfirmDeploy}"

                    if (timeoutConfirmDeploy) {
                        echo "timeoutConfirmDeployTime value: ${timeoutConfirmDeployTime}"
                        echo "timeoutConfirmDeployUnit value: ${timeoutConfirmDeployUnit}"
                    }


                    if (timeoutConfirmDeploy && timeoutConfirmDeployTime > 0 && isTimeoutConfirmDeployUnitValid) {
                        //Wrap input with timeout
                        timeout(time:timeoutConfirmDeployTime, unit:"${timeoutConfirmDeployUnit}") {
                            deploy = input message: 'Waiting for user approval',
                                    parameters: [choice(name: 'Continue and deploy?', choices: 'No\nYes', description: 'Choose "Yes" if you want to deploy this build')]
                        }
                    } else {
                        //Input without timeout
                        deploy = input message: 'Waiting for user approval',
                                parameters: [choice(name: 'Continue and deploy?', choices: 'No\nYes', description: 'Choose "Yes" if you want to deploy this build')]

                    }
                }
            } catch (err) {
                def user = err.getCauses()[0].getUser()
                if('SYSTEM'.equals(user.toString())) { //timeout
                    currentBuild.result = "FAILED"
                    throw new hudson.AbortException("Timeout on confirm deploy")
                }
            }
        }

        if (deploy == 'Yes') {
            node {
                checkout scm
                stage('OpenShift Deploy') {
                    echo "Deploying on OpenShift..."

                    openshift_route_hostname = openshiftDeployProject {
                        branchHY = branchNameHY
                        branch_type = branchType
                    }

                    openshift_route_hostname_with_protocol = utils.getRouteHostnameWithProtocol(openshift_route_hostname, false)

                }
            }

            echo "Openshift route hostname: ${openshift_route_hostname}"
            echo "Openshift route hostname (with protocol): ${openshift_route_hostname_with_protocol}"

            echo "params.jenkins.errorOnPostDeployTestsUnstableResult: ${params.jenkins.errorOnPostDeployTestsUnstableResult}"
            Boolean errorOnPostDeployTestsUnstableResult = false

            if (params.jenkins.errorOnPostDeployTestsUnstableResult != null) {
                errorOnPostDeployTestsUnstableResult = params.jenkins.errorOnPostDeployTestsUnstableResult.toBoolean()
            }

            echo "errorOnPostDeployTestsUnstableResult value: ${errorOnPostDeployTestsUnstableResult}"

            def tasks = [:]

            //Smoke tests
            if (branchType in params.testing.postdeploy.smokeTesting) {
                tasks["${Constants.SMOKE_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${Constants.SMOKE_TEST_TYPE} Tests") {
                                executePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = smoke_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = Constants.SMOKE_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = Constants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = Constants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${Constants.SMOKE_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${Constants.SMOKE_TEST_TYPE} tests..."
            }

            //Acceptance tests
            if (branchType in params.testing.postdeploy.acceptanceTesting) {
                tasks["${Constants.ACCEPTANCE_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${Constants.ACCEPTANCE_TEST_TYPE} Tests") {
                                executePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = acceptance_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = Constants.ACCEPTANCE_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = Constants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = Constants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${Constants.ACCEPTANCE_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${Constants.ACCEPTANCE_TEST_TYPE} tests..."
            }

            //Security tests
            if (branchType in params.testing.postdeploy.securityTesting) {
                tasks["${Constants.SECURITY_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${Constants.SECURITY_TEST_TYPE} Tests") {
                                executePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = security_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = Constants.SECURITY_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = Constants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = Constants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${Constants.SECURITY_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${Constants.SECURITY_TEST_TYPE} tests..."
            }


            //Executing smoke, acceptance and security tests in parallel
            parallel tasks


            //Performance tests
            if (branchType in params.testing.postdeploy.performanceTesting) {
                node('taurus') { //taurus
                    try {
                        stage("${Constants.PERFORMANCE_TEST_TYPE} Tests") {
                            executePerformanceTest {
                                pts_taurus_test_base_path = taurus_test_base_path
                                pts_acceptance_test_path = performance_test_path
                                pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                pts_performance_test_type = Constants.PERFORMANCE_TEST_TYPE
                            }
                        }
                    } catch (exc) {
                        def exc_message = exc.message
                        echo "${exc_message}"
                        if (errorOnPostDeployTestsUnstableResult) {
                            currentBuild.result = Constants.UNSTABLE_BUILD_RESULT
                        } else {
                            //Failed status
                            currentBuild.result = Constants.FAILURE_BUILD_RESULT
                            throw new hudson.AbortException("The ${Constants.PERFORMANCE_TEST_TYPE} tests stage has failures")
                        }
                    }
                }
            } else {
                echo "Skipping ${Constants.PERFORMANCE_TEST_TYPE} tests..."
            }

        } else {
            //User doesn't want to deploy
            //Failed status
            currentBuild.result = Constants.FAILURE_BUILD_RESULT
            throw new hudson.AbortException("The deploy on Openshift hasn't been confirmed")
        }



        stage('Notification') {
            echo "Sending Notifications..."

            /*
        if (!Constants.SUCCESS_BUILD_RESULT.equals(currentBuild.result)) {
            slackSend channel: '#ops-room', color: '#FF0000', message: "The pipeline ${currentBuild.fullDisplayName} has failed."
            hipchatSend (color: 'RED', notify: true, message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            emailext (
                    subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                    body: """<p>FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
      <p>Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>""",
                    recipientProviders: [[$class: 'DevelopersRecipientProvider']]
            )
        }
        */

        }

        stage('Remove old builds') {

            echo "params.maxOldBuildsToKeep: ${params.jenkins.maxOldBuildsToKeep}"
            echo "params.daysOldBuildsToKeep: ${params.jenkins.daysOldBuildsToKeep}"

            String maxOldBuildsToKeepParam = params.jenkins.maxOldBuildsToKeep
            String daysOldBuildsToKeepParam = params.jenkins.daysOldBuildsToKeep

            if (maxOldBuildsToKeepParam != null && maxOldBuildsToKeepParam.isInteger()) {
                maxOldBuildsToKeep = maxOldBuildsToKeepParam as Integer
            }

            if (daysOldBuildsToKeepParam != null && daysOldBuildsToKeepParam.isInteger()) {
                daysOldBuildsToKeep = daysOldBuildsToKeepParam as Integer
            }

            echo "maxOldBuildsToKeep: ${maxOldBuildsToKeep}"
            echo "daysOldBuildsToKeep: ${daysOldBuildsToKeep}"

            if (maxOldBuildsToKeep > 0 && daysOldBuildsToKeep > 0) {

                echo "Keeping last ${maxOldBuildsToKeep} builds"
                echo "Keeping builds for  ${daysOldBuildsToKeep} last days"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: "${daysOldBuildsToKeep}", numToKeepStr: "${maxOldBuildsToKeep}"]]]);

            } else if (maxOldBuildsToKeep > 0) {

                echo "Keeping last ${maxOldBuildsToKeep} builds"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: "${maxOldBuildsToKeep}"]]]);

            } else if (daysOldBuildsToKeep > 0) {

                echo "Keeping builds for  ${daysOldBuildsToKeep} last days"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: "${daysOldBuildsToKeep}", numToKeepStr: '']]]);

            } else {

                echo "Not removing old builds."

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '']]]);

            }

        }

    }

    echo "END GENERIC CONFIGURATION PROJECT (PGC)"

} //end of method

return this;

