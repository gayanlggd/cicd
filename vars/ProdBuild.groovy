def call() {

    def repoURL = ""
    def tagName = ""
    def envName = ""
    def DEPLOYMENT_PATH = "/Users/gayand/wso2/prod/wso2ei-6.5.0/repository/deployment/server/carbonapps"
    pipeline {
    agent { label 'master' }
        stages {
            stage("Clone") {
                steps{
                    script{
                        repoURL = scm.getUserRemoteConfigs()[0].getUrl().replaceAll("http://","").replaceAll("https://","")
                        tagName = env.TAG_NAME
                        envName = "Production" 
                        if(repoURL == null || repoURL.trim() == "") {
                            throw new Exception("The Bitbucket URL is Null or Empty.") 
                        }
                        deleteDir()
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'bitbucket-integration', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                        fullRepoUrl = "http://" + java.net.URLEncoder.encode(USERNAME, "UTF-8") + ":" + java.net.URLEncoder.encode(PASSWORD, "UTF-8") + "@" + repoUrl;
                        sh """
                            set +x
                            rm -rf ./source
                            git clone ${fullRepoUrl} --branch=${tagName} ./source
                        """
                        }
                    }
                }
            }
            stage('Maven Build') {
                steps {
                    script {
                        echo 'Building the integration project'
                        
                        def propLocation = ""
                        if(envName.equals("Production")) {
                            propLocation = "-P prod"
                        }
                        sh """
                            cd ./source/
                            mvn clean install -Dmaven.test.skip=true ${propLocation}
                        """  
                    }
                }
            }
            stage('Deploy Carbon Application') {
                steps {
                    script {
                        input(
                            message: 'Do you want to proceed?',
                            ok: "Yes",
                            submitter: admin
                        )
                        echo 'Deploying the Capp'
                        String cappLocation = sh(returnStdout: true, script: "find . -name \"*.car\"").trim()
                        if(cappList == null && cappList.trim() == "" && cappList.split('\n').length > 1 ) {
                            currentBuild.result = "FAILURE"
                            throw new Exception("Error finding the Capp or found more than one Capp in the Integration project.")
                        }
                        def pathFragments = cappLoc.split('/')
                        String cappName = pathFragments[pathFragments.length - 1].split("_")[0]
                        echo 'Backing up Capp ' +cappName
                        try {
                                sh """
                                    mkdir -p ./backup
                                    cp DEPLOYMENT_PATH/${cappName}*.car ./backup
                                """
                        } catch (Exception e) {
                            echo 'Ignoring Error ' +e
                        }
                        try{
                            sh """
                                rm -f DEPLOYMENT_PATH/${cappName}*.car
                            """
                            sleep 15
                            sh """
                                cp ${cappLocation} DEPLOYMENT_PATH
                            """
                        } catch(Exception e) {
                            echo 'Exception occured while deploying the Capp ' + e
                            echo 'Restoring the Capp from backup'
                            sh """
                                rm -f DEPLOYMENT_PATH/${cappName}*.car
                                cp ./backup + "/" + ${cappName} + "*.car" DEPLOYMENT_PATH
                            """
                            currentBuild.result = 'FAILURE'
                            throw new Exception("Error occured while Deploying the Capp" + e)
                        }
                    }
                }
            }
        }
        post {
            success {
                echo 'The process is successfully Completed....'
            }
        }
    }
}   
