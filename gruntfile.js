module.exports = function(grunt) {

  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    concat: {
      options: {
        separator: ';'
      },
      dist: {
        src: ['src/**/*.js'],
        dest: 'dist/<%= pkg.name %>.js'
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("dd-mm-yyyy") %> */\n'
      },
      dist: {
        files: {
          'dist/<%= pkg.name %>.min.js': ['<%= concat.dist.dest %>']
        }
      }
    },
    connect: {
      server: {
        options: {
          port: 9001,
          base: ['dist', 'UI']
        }
      }
    },
    less: {
      development: {
        files: {
          "dist/css/result.css": "UI/src/**/*.less"
        }
      },
      production: {
        options: {
          cleancss: true
        },
        files: {
          "dist/css/result.css": "UI/src/**/*.less"
        }
      }
    },
    jshint: {
      files: ['Gruntfile.js', 'UI/src/**/*.js', 'UI/test/**/*.js'],
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
    mochaTest: {
      test: {
        options: {
          reporter: 'spec'
        },
        src: ['<%= jshint.files %>']
      }
    },
    watch: {
      files: ['<%= jshint.files %>', 'UI/src/**/*.less', 'UI/**/*.html'],
      tasks: ['jshint', 'mochaTest', 'less:development'],
      options: {
        livereload: true
      }
    }
  });

  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-jshint');
  grunt.loadNpmTasks('grunt-mocha');
  grunt.loadNpmTasks('grunt-mocha-test');
  grunt.loadNpmTasks('grunt-contrib-watch');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-less');
  grunt.loadNpmTasks('grunt-contrib-connect');

  grunt.registerTask('test', ['jshint', 'mochaTest']);

  grunt.registerTask('default', ['jshint', 'mochaTest', 'less:production', 'concat', 'uglify']);
};
