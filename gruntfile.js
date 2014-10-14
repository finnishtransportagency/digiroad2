module.exports = function(grunt) {

  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    concat: {
      options: {
        separator: ';'
      },
      dist: {
        src: ['UI/src/**/*.js'],
        dest: 'dist/js/<%= pkg.name %>.js'
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("dd-mm-yyyy") %> */\n'
      },
      dist: {
        files: {
          'dist/js/<%= pkg.name %>.min.js': ['<%= concat.dist.dest %>']
        }
      }
    },
    cachebreaker: {
      options: {
        match: ['digiroad2.css'],
          replacement: 'md5',
          src: {
            path: 'dist/css/digiroad2.css'
          }
      },
      files: {
        src: ['UI/index.html']
      }
    },
    clean: ['dist'],
      connect: {
          server: {
              options: {
                  port: 9001,
                  base: ['dist', '.', 'UI'],
                  middleware: function (connect, opts) {
                      var config = [
                          // Serve static files.
                          connect.static(opts.base[0]),
                          connect.static(opts.base[1]),
                          connect.static(opts.base[2]),
                          // Make empty directories browsable.
                          connect.directory(opts.base[2])
                      ];
                      var proxy = require('grunt-connect-proxy/lib/utils').proxyRequest;
                      config.unshift(proxy);
                      return config;
                  }
              },
              proxies: [
                  {
                      context: '/api',
                      host: '127.0.0.1',
                      port: '8080',
                      https: false,
                      changeOrigin: false,
                      xforward: false
                  },
                  {
                      context: '/maasto',
                      host: 'karttamoottori.maanmittauslaitos.fi',
                      https: false,
                      changeOrigin: true,
                      xforward: false,
                      headers : {referer: 'http://www.paikkatietoikkuna.fi/web/fi/kartta'}
                  }

              ]
          }
      },
    less: {
      development: {
        files: {
          "dist/css/digiroad2.css": "UI/src/less/main.less"
        }
      },
      production: {
        options: {
          cleancss: true
        },
        files: {
          "dist/css/digiroad2.css": "UI/src/less/main.less"
        }
      }
    },
    jshint: {
      files: ['Gruntfile.js', 'UI/test/**/*.js', 'UI/src/**/*.js', 'UI/test_data/*.js'],
      options: {
        // options here to override JSHint defaults
        globals: {
          jQuery: true,
          console: true,
          module: true,
          document: true
        }
      }
    },
    mocha: {
      unit: {
        options: {
          // mocha options
          mocha: {
            ignoreLeaks: false
          },

          // URLs passed through as options
          urls: ['http://127.0.0.1:9001/test/test-runner.html'],

          // Indicates whether 'mocha.run()' should be executed in
          // 'bridge.js'
          run: false,
          log: true
        }
      },
      integration: {
        options: {
          mocha: { ignoreLeaks: true },
          urls: ['http://127.0.0.1:9001/test/integration-tests.html'],
          run: false,
          log: true,
          timeout: 10000,
          reporter: 'Spec'
        }
      }
    },
    watch: {
      files: ['<%= jshint.files %>', 'UI/src/**/*.less', 'UI/**/*.html'],
      tasks: ['jshint', 'mocha:unit', 'mocha:integration', 'less:development', 'configureProxies'],
      options: {
        livereload: true
      }
    },
    execute: {
      vallu_local_test: {
        options: {
          args: ['localhost', 9002]
        },
        src: ['vallu_test_server.js']
      }
    }
  });

  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-jshint');
  grunt.loadNpmTasks('grunt-mocha');
  grunt.loadNpmTasks('grunt-contrib-watch');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-less');
  grunt.loadNpmTasks('grunt-contrib-connect');
  grunt.loadNpmTasks('grunt-contrib-clean');
  grunt.loadNpmTasks('grunt-connect-proxy');
  grunt.loadNpmTasks('grunt-execute');
  grunt.loadNpmTasks('grunt-cache-breaker');

  grunt.registerTask('server', ['configureProxies:server', 'connect', 'less:development', 'watch']);

  grunt.registerTask('test', ['jshint', 'configureProxies:server', 'connect', 'mocha:unit', 'mocha:integration']);

  grunt.registerTask('default', ['jshint', 'configureProxies:server', 'connect', 'mocha:unit', 'mocha:integration', 'clean', 'less:production', 'concat', 'uglify', 'cachebreaker']);

  grunt.registerTask('deploy', ['clean', 'less:production', 'concat', 'uglify', 'cachebreaker']);

  grunt.registerTask('integration-test', ['jshint', 'configureProxies:server', 'connect', 'mocha:integration']);

  grunt.registerTask('vallu-test-server', ['execute:vallu_local_test', 'watch']);
};
