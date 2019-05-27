role :app, %w{web@production1}
role :web, %w{web@production1}
server 'production1', user: 'web', roles: %w{web app}