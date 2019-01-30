var municipalities;
var backend =  new CsvBackend();

$(function() {

  var rootElement = $('.form-box');
  getMunicipalities();
  console.time("load");
  getJobs();

  rootElement.find('#upload-btn').on('change', function () {
    $('#uploaded-file').val(this.value);
  });

  rootElement.find('#asset-selection').on('change', function () {
    $('.btn.btn-primary.btn-lg').prop('disabled', !$(this).val());
    $('#deleteCheckbox').prop('disabled', !$(this).val());
    $('#csvImportPoistaCheckbox').toggle($(this).val() === 'trafficsigns');
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
        },
        function(xhr) {
          spinnerOff();
          alert(xhr.responseText);
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

  function getJobs() {
     backend.getJobs().then(function(jobs){
      $('.job-status').html(buildTable(jobs));
    });
  }
});

var buildTable = function(jobs) {
  var table = function (jobs) {
    return $('<table>').addClass('job-status-table')
      .append(tableHeaderRow())
      .append(tableBodyRows(jobs));
  };

  var tableHeaderRow = function () {
    return '<thead><th id="date">Päivämäärä</th> <th id="file">Tiedosto</th> <th id="status">Tila</th> <th id="detail">Raportti</th></tr></thead>';
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
      '<tr>' +
      '<td headers="date">' + job.createdDate + '</td>' +
      '<td headers="file">' + job.fileName + '</td>' +
      '<td headers="status" >' + getStatusIcon(job.status, job.description) + '</td>' +
      '<td headers="detail">' + job.id + '</td>' +
      '</tr>';
  };
  return table(jobs);
};

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
