def call(Map config) {
    pipeline {
        agent { label 'python_agent' }
        stages {
            stage('Lint') {
                steps {
                    dir("${config.serviceDir}") {
                        sh 'pip install pylint'
                        sh 'pylint *.py --fail-under=1'
                    }
                }
            }

            stage('Security') {
                steps {
                    dir("${config.serviceDir}") {
                        sh 'pip install bandit'
                        sh 'bandit -r .'
                    }
                }
            }

            
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    dir("${config.serviceDir}") {
                        withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                            sh "docker login -u 'nbrar1282' -p '$TOKEN' docker.io"
                            sh "docker build -t ${config.imageName}:latest --tag ${config.imageName}:${config.tag} ."
                            sh "docker push ${config.imageName}:${config.tag}"
                        }
                    }
                }
            }

            stage('Deploy') {
              when {
                expression { return params.DEPLOY == true }
              }
              steps {
                sshagent(['ssh-to-3855vm']) {
                  sh """
                  ansible-playbook -i ~/ansible/inventory.yaml ~/ansible/deploy_project.yml
                  """
                }
              }
            }
        }

        parameters {
            booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Trigger Deploy Stage Manually')
        }
    }
}
