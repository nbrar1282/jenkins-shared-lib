def call(Map config) {
    pipeline {
        agent { label 'python_agent' }
        triggers {
            githubPush()
        }
        stages {
            stage('Lint') {
                steps {
                    dir("${config.serviceDir}") {
                        sh 'pylint *.py --fail-under=5'
                    }
                }
            }

            stage('Security') {
                steps {
                    dir("${config.serviceDir}") {
                        sh '''
                            ./venv/bin/bandit -r . -x ./venv -f json -o bandit_output.json
                            python3 -c "
                        import json, sys
                        with open('bandit_output.json') as f:
                            results = json.load(f)
                        high_issues = [
                            issue for issue in results.get('results', [])
                            if issue.get('issue_severity') in ('HIGH', 'CRITICAL')
                        ]
                        if high_issues:
                            print(f'\\n Found {len(high_issues)} HIGH/CRITICAL issues.')
                            for i in high_issues:
                                print(f\"- {i['filename']}:{i['line_number']} {i['issue_text']}\")
                            sys.exit(1)
                        else:
                            print('\\ No HIGH/CRITICAL issues found.')
                        "'''
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
                 sh '''
                  python3 -m venv venv
                  ./venv/bin/pip install ansible
                  ANSIBLE_HOST_KEY_CHECKING=False ./venv/bin/ansible-playbook -i /home/azureuser/ansible/inventory.yml /home/azureuser/ansible/deploy_project.yml
                '''
                }
              }
            }

        }

        parameters {
            booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Trigger Deploy Stage Manually')
        }
    }
}
