role :app, %w{web@training}
role :web, %w{web@training}
server 'training', user: 'web', roles: %w{web app}