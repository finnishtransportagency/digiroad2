role :app, %w{web@testing2}
role :web, %w{web@testing2}
server 'testing2', user: 'web', roles: %w{web app}