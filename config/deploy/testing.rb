role :app, %w{web@testing1}
role :web, %w{web@testing1}
server 'testing1', user: 'web', roles: %w{web app}

namespace :deploy do
  task :start_vallu_server do
    on roles(:all) do
      execute "killall -q node; exit 0"
      # Capistrano kills the vallu server before it gets to start up if sleep 1 is not defined
      execute "cd #{release_path} && (nohup grunt vallu-test-server >> ./src/main/webapp/vallu-server.log &) && sleep 1"
    end
  end

  after :publishing, :start_vallu_server
end