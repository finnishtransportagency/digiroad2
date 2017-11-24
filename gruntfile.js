module.exports = function(grunt) {
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    viitepkg: grunt.file.readJSON('viitepackage.json'),
    env: {
      options: {},
      development: {
        NODE_ENV: 'DEVELOPMENT'
      },
      staging: {
        NODE_ENV: 'STAGING'
      },
      production: {
        NODE_ENV: 'PRODUCTION'
      }
    },
    preprocess: {
      development: {
        files: {
          './UI/index.html': './UI/tmpl/index.html',
          './viite-UI/index.html': './viite-UI/tmpl/index.html'
        }
      },
      production: {
        files: {
          './UI/index.html': './UI/tmpl/index.html',
          './viite-UI/index.html': './viite-UI/tmpl/index.html'
        }
      }
    },
    concat: {
      options: {
        separator: ';'
      },
      dist: {
        files: {
          'dist/js/<%= pkg.name %>.js': ['UI/src/utils/StyleRule.js', 'UI/src/view/TrafficSignLabel.js', 'UI/src/view/AssetStyle.js', 'UI/src/view/ServiceRoadLabel.js', 'UI/src/view/MaintenanceRoadStyle.js', 'UI/src/view/WinterSpeedLimitStyle.js', 'UI/src/view/AssetLabel.js', 'UI/src/view/LinearAssetLabel.js', 'UI/src/model/TrafficSignsCollection.js', 'UI/src/**/*.js', '!**/ol-custom.js'],
          'dist-viite/js/<%= viitepkg.name %>.js': ['viite-UI/src/**/*.js', '!**/ol-custom.js']
        }
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("dd-mm-yyyy") %> */\n'
      },
      dist: {
        files: {
          'dist/js/<%= pkg.name %>.min.js': ['dist/js/<%= pkg.name %>.js'],
          'dist-viite/js/<%= viitepkg.name %>.min.js': ['dist-viite/js/<%= viitepkg.name %>.js']
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
        src: ['UI/index.html', 'viite-UI/index.html']
      }
    },
    clean: ['dist', 'dist-viite'],
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
            context: '/maasto',
            host: 'karttamoottori.maanmittauslaitos.fi',
            https: false,
            changeOrigin: true,
            xforward: false,
            headers: {referer: 'http://www.paikkatietoikkuna.fi/web/fi/kartta'}
          },
          {
            context: '/vkm',
            host: 'localhost',
            port: '8997',
            https: false,
            changeOrigin: false,
            xforward: false
          }
        ]
      },
      viite: {
        options: {
          port: 9001,
          base: ['dist-viite', '.', 'viite-UI'],
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
            context: '/arcgis',
            host: 'aineistot.esri.fi',
            https: true,
            port: '443',
            changeOrigin: true,
            xforward: false,
            headers: {referer: 'https://aineistot.esri.fi/arcgis/rest/services/Taustakartat/Harmaasavy/MapServer?f=jsapi'}
          },
          {
            context: '/maasto',
            host: 'karttamoottori.maanmittauslaitos.fi',
            https: false,
            changeOrigin: true,
            xforward: false,
            headers: {referer: 'http://www.paikkatietoikkuna.fi/web/fi/kartta'}
          },
          {
            context: '/vkm',
            host: 'localhost',
            port: '8997',
            https: false,
            changeOrigin: false,
            xforward: false
          },
          {
            context: '/test/components',
            host: 'localhost',
            port: '9001',
            https: false,
            changeOrigin: true,
            xforward: true,
            rewrite: {
              '^/test/components': '/components'
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
      viitedev: {
        files: {
          "dist-viite/css/viite.css": "viite-UI/src/less/main.less"
        }
      },
      production: {
        options: {
          cleancss: true
        },
        files: {
          "dist/css/digiroad2.css": "UI/src/less/main.less"
        }
      },
      viiteprod: {
        options: {
          cleancss: true
        },
        files: {
          "dist-viite/css/viite.css": "viite-UI/src/less/main.less"
        }
      }
    },
    jshint: {
      files: ['Gruntfile.js', 'UI/test/**/*.js', 'UI/src/**/*.js', 'UI/test_data/*.js', 'UI/src/',
        'viite-UI/test/**/*.js', 'viite-UI/src/**/*.js', 'viite-UI/test_data/*.js', 'viite-UI/src/' ],
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
          run: false,
          log: true,
          reporter: 'Spec'
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
      oth: {
        files: ['<%= jshint.files %>', 'UI/src/**/*.less', 'UI/**/*.html'],
        tasks: ['jshint', 'env:development', 'preprocess:development', 'less:development', 'mocha:unit', 'mocha:integration', 'configureProxies:oth'],
        options: {
          livereload: true
        }
      },
      viite: {
        files: ['<%= jshint.files %>', 'viite-UI/src/**/*.less', 'viite-UI/**/*.html'],
        tasks: ['jshint', 'env:development', 'preprocess:development', 'less:viitedev', 'mocha:unit', 'mocha:integration', 'configureProxies:viite'],
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
      prepare_openlayers: {
        cmd: 'npm install',
        cwd: './bower_components/openlayers/'
      },
      viite_build_openlayers: {
        cmd: 'node tasks/build.js ../../viite-UI/src/resources/digiroad2/ol3/ol-custom.js build/ol3.js',
        cwd: './bower_components/openlayers/'
      },
      oth_build_openlayers: {
        cmd: 'node tasks/build.js ../../UI/src/resources/digiroad2/ol3/ol-custom.js build/ol3.js',
        cwd: './bower_components/openlayers/'
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
  grunt.loadNpmTasks('grunt-env');
  grunt.loadNpmTasks('grunt-preprocess');
  grunt.loadNpmTasks('grunt-exec');

  var target = grunt.option('target') || 'production';

  grunt.registerTask('server', ['env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'less:development', 'less:viitedev', 'watch:oth']);

  grunt.registerTask('viite', ['env:development', 'configureProxies:viite', 'preprocess:development', 'connect:viite', 'less:viitedev', 'watch:viite']);

  grunt.registerTask('test', ['jshint', 'env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'mocha:unit', 'mocha:integration']);

  grunt.registerTask('viite-test', ['jshint', 'env:development', 'configureProxies:viite', 'preprocess:development', 'connect:viite', 'mocha:unit', 'mocha:integration']);

  grunt.registerTask('default', ['jshint', 'env:production', 'exec:prepare_openlayers', 'exec:oth_build_openlayers', 'exec:viite_build_openlayers', 'configureProxies:oth', 'preprocess:production', 'connect:oth', 'mocha:unit', 'mocha:integration', 'clean', 'less:production', 'less:viiteprod', 'concat', 'uglify', 'cachebreaker']);

  grunt.registerTask('deploy', ['clean', 'env:'+target, 'exec:prepare_openlayers', 'exec:oth_build_openlayers', 'exec:viite_build_openlayers', 'preprocess:production', 'less:production', 'less:viiteprod', 'concat', 'uglify', 'cachebreaker', 'save_deploy_info']);

  grunt.registerTask('integration-test', ['jshint', 'env:development', 'configureProxies:oth', 'preprocess:development', 'connect:oth', 'mocha:integration']);

  grunt.registerTask('viite-integration-test', ['jshint', 'env:development', 'configureProxies:viite', 'preprocess:development', 'connect:viite', 'mocha:integration']);

  grunt.registerTask('vallu-test-server', ['execute:vallu_local_test', 'watch']);

  grunt.registerTask('save_deploy_info',
      function() {
        var options = this.options({
          file: 'revision.properties'
        });

        var data = ('digiroad2.latestDeploy=' + grunt.template.today('dd-mm-yyyy HH:MM:ss'));
        grunt.file.write(options.file, data);

      }
  );
};