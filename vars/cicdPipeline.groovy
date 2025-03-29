def call(Map config) {
    pipeline {
        agent any
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
                steps {
                    dir("${config.serviceDir}") {
                        script {
                            dockerImage = docker.build("${config.imageName}")
                            docker.withRegistry('https://index.docker.io/v1/', 'DockerHub') {
                                dockerImage.push("${config.tag}")
                            }
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
