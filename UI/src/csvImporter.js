$(function() {
  var backend = new CsvBackend();
  var municipalities;
  var refresh;
  var rootElement = $('.form-box');

  var showImporter = function() {
    $('.csv-import').show();
    $('.job-content').empty();
  };

  var hideImporter = function() {
    $('.csv-import').hide();
    $('.job-content').show();
  };

  getMunicipalities();
  getJobs();
  showImporter();

  rootElement.find('#upload-btn').on('change', function () {
    $('#uploaded-file').val(this.value);
  });

  rootElement.find('#asset-selection').on('change', function () {
    clear();
    $('.btn.btn-primary.btn-lg').prop('disabled', !$(this).val());
    $('#deleteCheckbox').prop('disabled', !$(this).val());
    $('#csvImportPoistaCheckbox').toggle($(this).val() === 'trafficSigns');
    $('.mass-transit-stop-limit').toggle($(this).val() === 'massTransitStop');
  }).trigger('change');

  rootElement.find('#deleteCheckbox').on('change', function () {
    $(".municipalities").toggle();
    var emptySearch = _.isEmpty($('.municipalities').find("#municipalities_search_to, select[name*='municipalityNumbers']").find('option'));
    $('.btn.btn-primary.btn-lg').prop('disabled', $(this).prop('checked') && emptySearch);
  });

  rootElement.find('.form-group .municipalities .row').on('dblclick', function () {
    var disableStatus = _.isEmpty($('.municipalities').find("#municipalities_search_to, select[name*='municipalityNumbers']").find('option'));
    $('.btn.btn-primary.btn-lg').prop('disabled', disableStatus);
  });

  $('#csvImport').on('submit', (function(e) {
    e.preventDefault();
    var formData = new FormData(this);
    formData.delete('municipalityNumbers');
    var assetType = $('#asset-selection').find(":selected").val();
    function uploadFile() {
      backend.uploadFile(formData, assetType,
        function(data) {
          spinnerOff();
          addNewRow(data);
        },
        function(xhr) {
          spinnerOff();
          if(xhr.status === 403)
            alert("Vain operaattori voi suorittaa Excel-ajon");
          addNewRow(xhr.responseText);
        });
    }
    if ($('#deleteCheckbox').is(':checked')) {
      new GenericConfirmPopup('Haluatko varmasti poistaa kaikki jo aiemmin kunnan alueelle lisätyt liikennemerkit?', {
        container: '.csv-content',
        successCallback: function () {
          var optionValues = $('.municipalities').find("#municipalities_search_to, select[name*='municipalityNumbers']").find('option');
          _.each(optionValues, function (opt) {
            formData.append('municipalityNumbers', opt.value);
          });
          uploadFile();
          spinnerOn();
        }
      });
    } else {
      uploadFile();
      spinnerOn();
    }
  }));

  function clear() {
    var municipalityBox = $(".municipalities");
    $('input[type=checkbox]').prop('checked',false);
    municipalityBox.hide();
    municipalityBox.find("#municipalities_search, select[name*='municipalityNumbers']").find('option').remove();
    getMunicipalities();
  }

  function getMunicipalities() {
    if (_.isEmpty($('.municipalities').find("#municipalities_search").find('option'))) {
      if (_.isEmpty(municipalities)) {
        backend.getMunicipalities(
          function(result){
            municipalities = result;
            setMunicipalities();
          },
          function(){
            municipalities = [];
          }
        );
      } else
        setMunicipalities();
    }
  }

  function setMunicipalities() {
    _.forEach(municipalities, function (municipality) {
      $('.municipalities').find("#municipalities_search").append($('<option>', {
        value: municipality.id,
        text: municipality.name
      }));
    });
  }
  
  function getJobs() {
    backend.getJobs().then(function(jobs){
      if(!_.isEmpty(jobs))
        $('.job-status').empty().html(buildJobTable(jobs));

      _.forEach(jobs, function(job) {
        rootElement.find('.job-status-link#'+job.id).on('click', function (event) {
        getJob(event);
        });
      });
      scrollbarResize();
      refresh = setInterval(refreshJobs, 3000);
    });
  }
  
  function addNewRow(job) {
    if (!_.isEmpty(job)) {
      var newRow = jobRow(job);
      var table = $(".job-status-table tbody tr:first");
      
      if(_.isEmpty(table))
        $('.job-status').empty().html(buildJobTable([job]));
      else
        table.before(newRow);
      
      if(!refresh)
        refresh = setInterval(refreshJobs, 3000);
      scrollbarResize();
    }
  }
  
  function replaceRow(job) {
    if (!_.isEmpty(job)) {
      var newRow = jobRow(job);
      $("#"+job.id).replaceWith(newRow);
      rootElement.find('.job-status-link').on('click', function (event) {
        getJob(event);
      });
    }
  }
  
  var refreshJobs = function() {
    var jobsInProgress = $('.in-progress').map(function(){
      return $(this).attr('id');
    });
    
    if(!_.isEmpty(jobsInProgress)) {
      backend.getJobsByIds(jobsInProgress.toArray()).then(function(jobs){
        var endedJobs = _.filter(jobs, function(job){return job.status !== 1;});
        _.map(endedJobs, replaceRow);
      });
    } else {
      clearInterval(refresh);
      refresh = null;
    }
  };
    
  function getJob(evt){
    var id = $(evt.currentTarget).prop('id');
    backend.getJob(id).then(function(job){
      hideImporter();
      buildJobView(job);
    });
  }

  var buildJobView = function(job) {
    var jobView = $('.job-content');
    jobView.append('' +
      '<div class="job-content-box">' +
      '<header id="error-list-header">CSV-eräajon virhetilanteet: ' + job.fileName +
      '<a class="header-link" style="cursor: pointer;">Sulje</a>' +
      '</header>' +
      '<div class="error-list">' +
      '</div>'
    );
    jobView.find('.header-link').on('click', function(){
      showImporter();
    });
    $('.error-list').html(job.content);
  };

  var buildJobTable = function(jobs) {
    var table = function (jobs) {
      return $('<table>').addClass('job-status-table')
        .append(tableHeaderRow())
        .append(tableBodyRows(jobs));
    };

    var tableHeaderRow = function () {
      return '<thead><th id="date" class="date">Päivämäärä</th><th class="jobName">Tietolajityyppi</th><th id="file" class="file"">Tiedosto</th> <th id="status" class="status">Tila</th> <th id="detail" class="detail">Raportti</th></tr></thead>';
    };
    
    var tableBodyRows = function (jobs) {
      return $('<tbody>').append(tableContentRows(jobs));
    };
    var tableContentRows = function (jobs) {
      return _.map(jobs, function (job) {
        return jobRow(job).concat('');
      });
    };
    return table(jobs);
  };
  
  var jobNameConvert =
    { 'import_roadLinks': 'Täydentävät tielinkit',
      'import_trafficSigns': 'Liikennemerkit',
      'import_maintenanceRoads': 'Rautateiden huoltotie',
      'import_massTransitStop': 'Joukkoliikennepysäkit',
      'import_obstacles': 'Esterakennelma',
      'import_trafficLights': 'Liikennevalot',
      'import_railwayCrossings': 'Tasoristeys',
      'import_pedestrianCrossings': 'Suojatie',
      'import_servicePoints': 'Palvelupiste'
    };
  
  var jobRow = function (job) {
    return '' +
      '<tr class="' + (job.status === 1 ? 'in-progress' : '') + '" id="' + job.id + '">' +
        '<td headers="date" class="date">' + job.createdDate + '</td>' +
        '<td headers="jobName" class="jobName">' + (_.isUndefined(jobNameConvert[job.jobName]) ? "" : jobNameConvert[job.jobName]) + '</td>' +
        '<td headers="file" class="file" id="file">' + job.fileName + '</td>' +
        '<td headers="status" class="status">' + getStatusIcon(job.status, job.statusDescription) + '</td>' +
        '<td headers="detail" class="detail">' + (job.status > 2 ? '<button class="btn btn-block btn-primary job-status-link" id="'+ job.id + '">Avaa</button>' : '') + '</td>' +
      '</tr>';
  };

  var scrollbarResize = function () {
    if ( $('.job-status tbody tr').length >= 5)
      $('.job-status thead').css("width", "calc(100% - 17px)");
  };
});

var getStatusIcon = function(status, description) {
  var icon = {
    1: "images/csv-status-icons/clock-outline.png",
    2: "images/csv-status-icons/check-icon.png",
    3: "images/csv-status-icons/not-ok-check-icon.png",
    4: "images/csv-status-icons/error-icon-small.png",
    99: "images/csv-status-icons/unknown-error.png"
  };
  return '<img src="' + icon[status] + '" title="' + description + '"/>';
};

var spinnerOn = function() {
  $('.content-box').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
};

var spinnerOff = function() {
  $('.spinner-overlay').remove();
};
