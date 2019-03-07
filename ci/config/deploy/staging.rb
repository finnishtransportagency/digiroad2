role :app, %w{web@172.17.204.46}
role :web, %w{web@172.17.204.46}
server '172.17.204.46', user: 'web', roles: %w{web app}