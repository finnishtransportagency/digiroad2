(function (root) {
  root.csvReportsWorkList = function () {
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/csvReports";
    this.title = 'Raportointityökalu';
    var backend;
    var municipalities;
    var assetsList;
    var refresh;


    this.initialize = function (mapBackend) {
      backend = mapBackend;
     // addMultiselectScript();

      me.bindEvents();
    };


   /* function addMultiselectScript() {
      var script = document.createElement('script');
      script.type = 'text/javascript';
      script.src = 'node_modules/multiselect-two-sides/dist/js/multiselect.min.js';

      document.getElementsByTagName('head')[0].appendChild(script);
    }*/

    this.bindEvents = function () {
      eventbus.on('csvReports:select', function (listP) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');

        me.generateWorkList(listP);

        me.addSpinner();
      });

    };

    function getMunicipalities() {
      if (_.isEmpty($("#municipalities_search").find('option'))) {
        if (_.isEmpty(municipalities)) {

          backend.getMunicipalities().then(function (result) {
            municipalities = result;
            setMunicipalities();
          });

        } else
          setMunicipalities();
      }
    }

    function getAssetsType() {
      if (_.isEmpty($("#assets_search").find('option'))) {
        if (_.isEmpty(assetsList)) {

          backend.getAssetTypes().then(function (result) {
            assetsList = result;
            setAssets();
          });
        } else
          setAssets();
      }
    }

    function setMunicipalities() {
      $('#municipalities_search').append($('<option>', {
        value: "1000",
        text: "Kaikki kunnat"  /* all municipalities */
      }));

      _.forEach(municipalities, function (municipality) {
        $('#municipalities_search').append($('<option>', {
          value: municipality.id,
          text: municipality.name
        }));
      });
    }

    function setAssets() {
      var topOptions = [
        {"id": "1", "value": "Kaikki tietolajit"}, /* all assets */
        {"id": "2", "value": "Pistemäiset tietolajit"}, /* point assets */
        {"id": "3", "value": "Viivamaiset tietolajit"} /* linear assets */
      ];

      _.forEach(topOptions, function (option) {
        $('#assets_search').append($('<option>', {
          value: option.id,
          text: option.value
        }));
      });

      _.forEach(assetsList, function (asset) {
        $('#assets_search').append($('<option>', {
          value: asset.id,
          text: asset.name
        }));
      });
    }


    function populatedMultiSelectBoxes() {
      getMunicipalities();
      getAssetsType();
    }


    function multiSelectBoxForMunicipalities() {
      jQuery('#municipalities_search').multiselect({
        search: {
          left:
              '<label class="control-label labelBoxLeft">Kaikki kunnat</label>' +
              '<input type="text" id = "left_municipalities" class="form-control" placeholder="Kuntanimi" />',
          right:
              '<label class="control-label labelBoxRight">Valitut Kunnat</label>' +
              '<input type="text" id = "right_municipalities" class="form-control" placeholder="Kuntanimi" />'
        },
        sort: {
          left: function (a, b) {
            return (a.value == "1000" || b.value == "1000") ? 1 : a.text > b.text ? 1 : -1;
          }
        },
        fireSearch: function (value) {
          return value.length >= 1;
        }
      });
    }

    function multiSelectBoxForAssets() {
      jQuery('#assets_search').multiselect({
        search: {
          left:
              '<label class="control-label labelBoxLeft">Kaikki Tietolajit</label>' +
              '<input type="text" id = "left_assets" class="form-control" placeholder="Tietolajit Nimi" />',
          right:
              '<label class="control-label labelBoxRight">Valitut Tietolajit</label>' +
              '<input type="text" id = "right_assets" class="form-control" placeholder="Tietolajit Nimi" />'
        },
        sort: {
          left: function (a, b) {
            var topElems = ["1", "2", "3"];
            return (_.includes(topElems, a.value) || _.includes(topElems, b.value)) ? 1 : a.text > b.text ? 1 : -1;
          }
        },
        fireSearch: function (value) {
          return value.length >= 1;
        }
      });
    }

    function saveForm() {
      var formData = {};

      var municipalityValues = $("#municipalities_search_to, select[name*='municipalityNumbers']").find('option');
      formData.municipalityNumbers = _.map(municipalityValues, function (municipality) {
         return municipality.value;
      });

      var assetValues = $("#assets_search_to, select[name*='assetNumbers']").find('option');
      formData.assetNumbers = _.map(assetValues, function (asset) {
         return asset.value;
      });

      backend.postGenerateCsvReport(formData.municipalityNumbers, formData.assetNumbers,
          function(data) {
            me.removeSpinner();
            addNewRow(data);
          },
          function(xhr) {
            me.removeSpinner();
            if(xhr.status === 403)
              alert("Vain operaattori voi suorittaa Excel-ajon");
            addNewRow(xhr.responseText);
          });
    }

    this.generateWorkList = function (listP) {
      listP.then(function (result) {
        me.removeSpinner();

        $('#work-list').html('' +
            '<div style="overflow: auto;">' +
              '<div class="page">' +
                '<div class="content-box">' +
                  '<header id="work-list-header">' + me.title +
                    '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
                  '</header>' +
                  '<div class="work-list">' +
                  '</div>' +
              '</div>' +
            '</div>'
        );

        // $('.page').find('#work-list-header').append($('<a class="header-link"></a>').attr('href', '#work-list/municipality/' + result.municipalityCode).html('Kuntavalinta'));
        $('#work-list .work-list').html(me.workListItemTable(result));

        populatedMultiSelectBoxes();

        multiSelectBoxForMunicipalities();
        multiSelectBoxForAssets();

        $('#send_btn').click( saveForm );

       $('#municipalities_search_rightSelected, #municipalities_search_leftSelected, #assets_search_rightSelected, #assets_search_leftSelected').on('click', enableSubmitButton);
       $('#municipalities_search, #municipalities_search_to, #assets_search, #assets_search_to').on('dblclick', enableSubmitButton);

      });
    };



    function enableSubmitButton() {
      var isMunicipalityEmpty = _.isEmpty($("#municipalities_search_to, select[name*='municipalityNumbers']").find('option'));
      var isAssetEmpty = _.isEmpty($("#assets_search_to, select[name*='assetNumbers']").find('option'));

      var disableStatus = !isAssetEmpty && !isMunicipalityEmpty;
      $('.btn.btn-primary.btn-lg').prop('disabled', !disableStatus);
    }


    this.workListItemTable = function (result) {

      var downloadCsvButton = $('<a />').addClass('btn btn-primary btn-download')
          .text('Lataa CSV')
          .append("<img src='images/icons/export-icon.png'/>")
          .click(function () {
            var html = $(".work-list").find(" table");
            var date = new Date();
            // When extracting the month from the date (date.getMonth()), we add 1 to the result since the method returns the month as a number from 0 to 11.
            // me.exportTableToCSV(result.municipalityName, html, me.title.toLowerCase()  + "_" + result.municipalityCode + "_" + String(date.getDate()).padStart(2, '0') + "-" + String(date.getMonth() + 1).padStart(2, '0') + "-" + String(date.getFullYear())+ ".csv");
          });


      var form =
          '<div class="form form-horizontal" id="csvExport" role="form" >' +
          '<div class="form-group">' +
              '<div class="form-group" disabled="disabled" >' +
                '<div class="row">' +
                  '<div class="col-xs-5">' +
                    '<select id="municipalities_search" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                  '<div class="col-xs-2">' +
                    '<button type="button" id="municipalities_search_rightSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &gt; </button>' +
                    '<button type="button" id="municipalities_search_leftSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &lt; </button>' +
                  '</div>' +
                  '<div class="col-xs-5">' +
                    '<select name="municipalityNumbers" id="municipalities_search_to" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="form-group">' +
              '<div class="form-group" disabled="disabled" >' +
                '<div class="row">' +
                  '<div class="col-xs-5">' +
                    '<select id="assets_search" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                  '<div class="col-xs-2">' +
                    '<button type="button" id="assets_search_rightSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &gt; </button>' +
                    '<button type="button" id="assets_search_leftSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &lt; </button>' +
                  '</div>' +
                  '<div class="col-xs-5">' +
                    '<select name="assetNumbers" id="assets_search_to" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="form-controls" style="text-align: right;">' +
              '<button id="send_btn" class="btn btn-primary btn-report">Luo raportti</button>' +
            '</div>' +
          '</div>'+
          '<div class="job-status">' +
          '</div>'+
          '<div class="job-content">'+
          '</div>';

      return form;
    };


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

    function replaceRow(job) {
      if (!_.isEmpty(job)) {
        var newRow = jobRow(job);
        $("#"+job.id).replaceWith(newRow);
        rootElement.find('.job-status-link').on('click', function (event) {
          getJob(event);
        });
      }
    }

    var hideImporter = function() {
      $('#csvExport').hide();
      $('.job-content').show();
    };

    var showImporter = function() {
      $('.csvExport').show();
      $('.job-content').empty();
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

    var buildJobTable = function(jobs) {
      var table = function (jobs) {
        return $('<table>').addClass('job-status-table')
            .append(tableHeaderRow())
            .append(tableBodyRows(jobs));
      };

      var tableHeaderRow = function () {
        return ''+
          '<thead>'+
              '<tr>'+
                '<th id="date" class="date">Päivämäärä</th>'+
                '<th class="jobName">Tietolajityyppi</th>'+
                '<th id="file" class="file"">Tiedosto</th>'+
                '<th id="status" class="status">Tila</th>'+
                '<th id="detail" class="detail">Raportti</th>'+
            '</tr>'+
          '</thead>';
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

    var jobRow = function (job) {
      return '' +
          '<tr class="' + (job.status === 1 ? 'in-progress' : '') + '" id="' + job.id + '">' +
            '<td headers="date" class="date">' + job.createdDate + '</td>' +
            '<td headers="jobName" class="jobName">Reports</td>'+ /*+ (_.isUndefined(jobNameConvert[job.jobName]) ? "" : jobNameConvert[job.jobName]) + '</td>' +*/
            '<td headers="file" class="file" id="file">' + job.fileName + '</td>' +
            '<td headers="status" class="status">' + getStatusIcon(job.status, job.statusDescription) + '</td>' +
            '<td headers="detail" class="detail">' + (job.status > 2 ? '<button class="btn btn-block btn-primary job-status-link" id="'+ job.id + '">Avaa</button>' : '') + '</td>' +
          '</tr>';
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

    var scrollbarResize = function () {
      if ( $('.job-status tbody tr').length >= 5)
        $('.job-status thead').css("width", "calc(100% - 17px)");
    };

  };
})(this);