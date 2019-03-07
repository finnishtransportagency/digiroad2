role :app, %w{web@production2}
role :web, %w{web@production2}
server 'production2', user: 'web', roles: %w{web app}