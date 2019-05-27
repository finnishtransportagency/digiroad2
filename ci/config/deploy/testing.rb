role :app, %w{web@testing1}
role :web, %w{web@testing1}
server 'testing1', user: 'web', roles: %w{web app}