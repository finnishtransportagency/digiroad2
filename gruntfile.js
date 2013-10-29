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
    less: {
      development: {
        options: {
          paths: ['src/**/*.less']
        },
        files: {
          "dist/css/result.css": "src/**/*.less"
        }
      },
      production: {
        options: {
          paths: ['src/**/*.less'],
          cleancss: true
        },
        files: {
          "dist/css/result.css": "src/**/*.less"
        }
      }
    },
    jshint: {
      files: ['Gruntfile.js', 'src/**/*.js', 'test/**/*.js'],
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
      files: ['<%= jshint.files %>', 'src/**/*.less'],
      tasks: ['jshint', 'mochaTest', 'less:development']
    }
  });

  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-jshint');
  grunt.loadNpmTasks('grunt-mocha');
  grunt.loadNpmTasks('grunt-mocha-test');
  grunt.loadNpmTasks('grunt-contrib-watch');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-less');

  grunt.registerTask('test', ['jshint', 'mochaTest']);

  grunt.registerTask('default', ['jshint', 'mochaTest', 'less:production', 'concat', 'uglify']);
};
