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
    $('.btn.btn-primary.btn-lg').prop('disabled', !$(this).val());
    $('#deleteCheckbox').prop('disabled', !$(this).val());
    $('#csvImportPoistaCheckbox').toggle($(this).val() === 'trafficSigns');
    $('.mass-transit-stop-limit').toggle($(this).val() === 'massTransitStop');
  }).trigger('change');

  rootElement.find('#deleteCheckbox').on('change', function () {
    $(".municipalities").toggle($(this).val());
    $('.btn.btn-primary.btn-lg').prop('disabled', $(this).val());
  });

  rootElement.find('.form-group .municipalities .row').on('dblclick', function () {
    var disableStatus = _.isEmpty($('.municipalities').find("#municipalities_search_to, select[name*='municipalityNumbers']").find('option'));
    $('.btn.btn-primary.btn-lg').prop('disabled', disableStatus);
  });

  $('#csvImport').on('submit', (function(e) {
    e.preventDefault();
    var formData = new FormData(this);
    var assetType = $('#asset-selection').find(":selected").val();
    function uploadFile() {
      backend.uploadFile(formData, assetType,
        function() {
          spinnerOff();
          getJobs();
        },
        function(xhr) {
          spinnerOff();
          if(xhr.status === 403)
            alert("Vain operaattori voi suorittaa Excel-ajon");
          getJobs();
        });
    }
    if ($('#deleteCheckbox').is(':checked')) {
      new GenericConfirmPopup('Haluatko varmasti poistaa kaikki jo aiemmin kunnan alueelle lisätyt liikennemerkit?', {
        successCallback: function () {
          var optionValues = $('.municipalities').find("#municipalities_search_to, select[name*='municipalityNumbers']").find('option');
          _.each(optionValues, function (opt) {
            opt.selected = true;
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

  jQuery(document).ready(function($) {
    $('#municipalities_search').multiselect({
      search: {
        left:
          '<label class="control-label labelBoxLeft">Kaikki kunnat</label>' +
          '<input type="text" id = "left_municipalities" class="form-control" placeholder="Kuntanimi" />',
        right:
          '<label class="control-label labelBoxRight">Valitut Kunnat</label>' +
          '<input type="text" id = "right_municipalities" class="form-control" placeholder="Kuntanimi" />'
      },
      fireSearch: function(value) {
        return value.length >= 1;
      }
    });
  });

  function getJobs() {
     backend.getJobs().then(function(jobs){
      if(!_.isEmpty(jobs)) {
        $('.job-status').empty().html(buildJobTable(jobs));
        rootElement.find('.job-status-link').on('click', function (event) {
          getJob(event);
        });
        if(!refresh)
          refresh = setInterval(refreshJobs, 30000);
      }
    });
  }

  var refreshJobs = function() {
    var jobsInProgress = $('.in-progress').map(function(){
      return $(this).attr('id');
    });
    if(!_.isEmpty(jobsInProgress)){
      backend.getJobsByIds(jobsInProgress.toArray()).then(function(jobs){
        if(_.some(jobs, function(job){return job.status !== 1;}))
          getJobs();
      });
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
      '<header id="error-list-header">' + 'CSV-eräajon virhetilanteet: ' + job.fileName +
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
      return '<thead><th id="date">Päivämäärä</th> <th id="file" style="width:50%">Tiedosto</th> <th id="status">Tila</th> <th id="detail">Raportti</th></tr></thead>';
    };
    var tableBodyRows = function (jobs) {
      return $('<tbody>').append(tableContentRows(jobs));
    };
    var tableContentRows = function (jobs) {
      return _.map(jobs, function (job) {
        return jobRow(job).concat('');
      });
    };
    var jobRow = function (job) {
      return '' +
        '<tr class="' + (job.status === 1 ? 'in-progress' : '') + '" id="' + job.id + '">' +
        '<td headers="date">' + job.createdDate + '</td>' +
        '<td headers="file" id="file">' + job.fileName + '</td>' +
        '<td headers="status">' + getStatusIcon(job.status, job.statusDescription) + '</td>' +
        '<td headers="detail">' + (job.status > 2 ? '<button class="btn btn-block btn-primary job-status-link" id="'+ job.id + '">Avaa</button>' : '') + '</td>' +

        '</tr>';
    };
    return table(jobs);
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
  $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
};

var spinnerOff = function() {
  $('.spinner-overlay').remove();
};
