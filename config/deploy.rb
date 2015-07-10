# config valid only for Capistrano 3.1
lock '3.1.0'

set :application, 'digiroad2'
set :repo_url, 'git@github.com:finnishtransportagency/digiroad2.git'

# Default branch is :master
# ask :branch, proc { `git rev-parse --abbrev-ref HEAD`.chomp }

# Default deploy_to directory is /var/www/my_app
set :deploy_to, "/home/web/digiroad2"

# Default value for :scm is :git
# set :scm, :git

# Default value for :format is :pretty
# set :format, :pretty

# Default value for :log_level is :debug
# set :log_level, :info

# Default value for :pty is false
set :pty, true

# Default value for :linked_files is []
# set :linked_files, %w{config/database.yml}

# Default value for linked_dirs is []
# set :linked_dirs, %w{bin log tmp/pids tmp/cache tmp/sockets vendor/bundle public/system}

# Default value for default_env is {}
# set :default_env, { path: "/opt/ruby/bin:$PATH" }

# Default value for keep_releases is 5
# set :keep_releases, 5

namespace :deploy do

  task :start do
    on roles(:all), in: :parallel do
      execute "cp #{deploy_to}/newrelic/* #{release_path}/."
      execute "cd #{release_path} && chmod 700 start.sh"
      execute "cd #{release_path} && nohup ./start.sh"
    end
  end

  task :prepare_release do
    on roles(:all) do |host|
      execute "cd #{release_path} && npm install && bower install && grunt deploy"
      execute "cd #{deploy_path} && mkdir #{release_path}/digiroad2-oracle/lib && cp oracle/* #{release_path}/digiroad2-oracle/lib/."
      execute "mkdir -p #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}"
      execute "cd #{deploy_path} && cp bonecp.properties #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp conversion.bonecp.properties #{release_path}/digiroad2-oracle/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp authentication.properties #{release_path}/conf/#{fetch(:stage)}/."
      execute "cd #{deploy_path} && cp ftp.conf #{release_path}/."
      execute "cd #{release_path} && ./sbt -Ddigiroad2.env=#{fetch(:stage)} assembly"
      execute "cd #{release_path} && rsync -a dist/ src/main/webapp/"
      execute "cd #{release_path} && rsync -a --exclude-from 'copy_exclude.txt' UI/ src/main/webapp/"
      execute "cd #{release_path} && rsync -a bower_components src/main/webapp/"
      execute "killall -q java; exit 0"
      execute "cd #{release_path} && ./sbt -Ddigiroad2.env=#{fetch(:stage)} 'project digiroad2-oracle' 'test:run-main fi.liikennevirasto.digiroad2.util.DatabaseMigration'"
    end
  end

  before :publishing, :prepare_release

  after :publishing, :start
end
