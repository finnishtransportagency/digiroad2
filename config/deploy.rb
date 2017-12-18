lock '3.1.0'
set :application, 'digiroad2'
set :repo_url, 'https://github.com/finnishtransportagency/digiroad2.git'
set :branch, ENV['REVISION'] || ENV['BRANCH_NAME'] || 'master'
set :deploy_to, "/home/web/digiroad2"
set :pty, true
set :log_level, :info
set :grunt_target, ENV['GRUNT_TARGET'] || ''

namespace :deploy do
  task :start do
    on roles(:all), in: :parallel do
      execute "cp #{deploy_to}/newrelic/* #{release_path}/."
      execute "cd #{release_path} && chmod 700 start.sh"
      execute "cd #{release_path} && nohup ./start.sh"
      execute "cd #{release_path} && tmux new -s 'waiting for your commands' -d"
    end
  end

  task :prepare_release do
    on roles(:all) do |host|
      execute "tmux kill-server || true"
      execute "cd #{release_path} && npm install && yarn install && grunt deploy --target=#{fetch(:grunt_target)}"
      execute "cd #{deploy_path} && mkdir #{release_path}/digiroad2-oracle/lib && cp oracle/* #{release_path}/digiroad2-oracle/lib/."
      execute "mkdir -p #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}"
      execute "cd #{deploy_path} && cp bonecp.properties #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp conversion.bonecp.properties #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp authentication.properties #{release_path}/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp keys.properties #{release_path}/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp keys.properties #{release_path}/digiroad2-oracle/src/test/resources/."
      execute "cd #{release_path} && cp revision.properties #{release_path}/conf/#{fetch(:stage)}/. || echo 'SKIP: No revision information available'"
      execute "cd #{release_path} && ./sbt -Ddigiroad2.env=#{fetch(:stage)} assembly"
      execute "cd #{release_path} && rsync -a dist/ src/main/webapp/"
      execute "cd #{release_path} && rsync -a dist-viite/ src/main/webapp/viite/"
      execute "cd #{release_path} && rsync -a --exclude-from 'copy_exclude.txt' UI/ src/main/webapp/"
      execute "cd #{release_path} && rsync -a --exclude-from 'copy_exclude.txt' viite-UI/ src/main/webapp/viite/"
      execute "cd #{release_path} && rsync -a node_modules src/main/webapp/"
      execute "cd #{release_path} && rsync -a node_modules src/main/webapp/viite/"
      execute "killall -q java; exit 0"
      execute "cd #{release_path} && ./sbt -Ddigiroad2.env=#{fetch(:stage)} 'project digiroad2-oracle' 'test:run-main fi.liikennevirasto.digiroad2.util.DatabaseMigration'"
    end
  end

  before :publishing, :prepare_release

  after :publishing, :start
end
