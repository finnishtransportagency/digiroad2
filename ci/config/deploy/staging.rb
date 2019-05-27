role :app, %w{web@gateway}
role :web, %w{web@gateway}
server 'gateway', user: 'web', roles: %w{web app}