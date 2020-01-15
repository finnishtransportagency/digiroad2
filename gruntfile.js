module.exports = function(grunt) {
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    properties: {
      app: 'conf/dev/keys.properties'
    },
    env: {
      options: {},
      development: {
        NODE_ENV: 'DEVELOPMENT'
      },
      staging: {
        NODE_ENV: 'STAGING'
      },
      integration: {
        NODE_ENV: 'PRODUCTION'
      },
      production: {
        NODE_ENV: 'PRODUCTION'
      }
    },
    preprocess: {
      development: {
        files: {
          './UI/index.html': './UI/tmpl/index.html'
        }
      },
      production: {
        files: {
          './UI/index.html': './UI/tmpl/index.html'
        }
      }
    },
    concat: {
      options: {
        separator: ';'
      },
      dist: {
        files: {
          'dist/js/<%= pkg.name %>.js': ['UI/src/utils/styleRule.js', 'UI/src/view/point_asset/trafficSignLabel.js', 'UI/src/view/providers/assetStyle.js', 'UI/src/view/linear_asset/serviceRoadLabel.js', 'UI/src/view/point_asset/heightLimitLabel.js', 'UI/src/view/point_asset/weightLimitLabel.js', 'UI/src/view/point_asset/widthLimitLabel.js', 'UI/src/view/linear_asset/serviceRoadStyle.js', 'UI/src/view/linear_asset/winterSpeedLimitStyle.js', 'UI/src/view/linear_asset/pavedRoadStyle.js', 'UI/src/view/providers/assetLabel.js', 'UI/src/view/linear_asset/linearAssetLabel.js', 'UI/src/controller/assetsVerificationCollection.js', 'UI/src/controller/trafficSignsCollection.js', 'UI/src/**/*.js', '!**/ol-custom.js']
        }
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("dd-mm-yyyy") %> */\n'
      },
      dist: {
        files: {
          'dist/js/<%= pkg.name %>.min.js': ['dist/js/<%= pkg.name %>.js']
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
      oth: {
        options: {
          port: 9001,
          base: ['dist', '.', 'UI'],
          middleware: function(connect, opts) {
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
            context: '/digiroad/api-docs',
            host: '127.0.0.1',
            port: '8080',
            https: false,
            changeOrigin: true,
            xforward: false,
            rewrite: {
              '^/digiroad/api-docs': '/api-docs'
            }
          },
          {
            context: '/maasto',
            // host: '172.17.204.46',
            host: '172.17.206.180',
            port: '8080',
            https: false,
            changeOrigin: true,
            xforward: false,
            rewrite: {
              '^/maasto': '/digiroad/maasto'
            }
          },
          {
            context: '/vkm-api',
            host: 'julkinen.vayla.fi',
          	port: '443',
            https: true,
            changeOrigin: true,
            xforward: false,
            headers: {
              host: 'https://julkinen.vayla.fi/vkm-api/'
            }
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
      files: ['Gruntfile.js', 'UI/test/**/*.js', 'UI/src/**/*.js', 'UI/test_data/*.js', 'UI/src/' ],
      options: {
        reporterOutput: "",
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
          timeout: 50000,
          run: false,
          log: true,
          reporter: 'Spec'
        }
      },
      integration: {
        options: {
          mocha: {ignoreLeaks: true},
          urls: ['http://127.0.0.1:9001/test/integration-tests.html'],
          run: false,
          log: true,
          timeout: 50000,
          reporter: 'Spec'
        }
      },
      options: {
        growlOnSuccess: false
      }
    },
    watch: {
      oth: {
        files: ['<%= jshint.files %>', 'UI/src/**/*.less', 'UI/**/*.html'],
        tasks: ['properties', 'jshint', 'env:development', 'preprocess:development', 'less:development', 'mocha:unit', 'mocha:integration', 'configureProxies:oth'],
        options: {
          livereload: true
        }
      }
    },
    execute: {
      vallu_local_test: {
        options: {
          args: ['localhost', 9002]
        },
        src: ['vallu_test_server.js']
      }
    },
    exec: {
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
  grunt.loadNpmTasks('grunt-env');
  grunt.loadNpmTasks('grunt-preprocess');
  grunt.loadNpmTasks('grunt-exec');
  grunt.loadNpmTasks('grunt-properties-reader');

  var target = grunt.option('target') || 'production';

  grunt.registerTask('server', ['properties', 'env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'less:development', 'watch:oth']);

  grunt.registerTask('test', ['properties', 'jshint', 'env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'mocha:unit', 'mocha:integration']);

  grunt.registerTask('default', ['properties', 'jshint', 'env:production', 'configureProxies:oth', 'preprocess:production', 'connect:oth', 'mocha:unit', 'mocha:integration', 'clean', 'less:production', 'concat', 'uglify', 'cachebreaker']);

  grunt.registerTask('deploy', ['clean', 'env:' + target, 'preprocess:production', 'less:production', 'concat', 'uglify', 'cachebreaker', 'save_deploy_info']);

  grunt.registerTask('integration-test', ['properties', 'jshint', 'env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'mocha:integration']);

  grunt.registerTask('vallu-test-server', ['execute:vallu_local_test', 'watch']);

  grunt.registerTask('test-concat', ['concat']);

  grunt.registerTask('save_deploy_info',
    function () {
      var options = this.options({
        file: 'revision.properties'
      });

      var data = ('digiroad2.latestDeploy=' + grunt.template.today('dd-mm-yyyy HH:MM:ss'));
      grunt.file.write(options.file, data);

    }
  );
};