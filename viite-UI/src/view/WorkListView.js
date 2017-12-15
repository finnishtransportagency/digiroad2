(function (root) {
  var counter = 0;
  var floatingLinksTable = function(layerName, floatingLinks, elyCode) {
    counter += floatingLinks.length;
    var elyHeader = function(elyCode) {
      return $('<h2/>').html("ELY " + elyCode );
    };

    var tableContentRows = function(links) {
      return _.map(links, function(link) {
        return $('<tr/>').append($('<td align=left style="font-size: smaller;"/>')
          .append(floatingDescription('TIE', link.roadNumber))
          .append(floatingDescription('OSA', link.roadPartNumber))
          .append(floatingDescription('AJR', link.trackCode))
          .append(floatingDescription('AET', link.startAddressM))
          .append(floatingDescription('LET', link.endAddressM)))
          .append($('<td align=right />').append(floatingLink(link)));
      });
    };
    var floatingLink = function(floating) {
      var link = '#' + layerName + '/' + floating.linkId;
      return $('<a style="font-size: smaller; class="work-list-item"/>').attr('href', link).html(link);
    };

    var floatingDescription = function(desc, value){
      return $('<td align=left style="width: 100px;"> <b>'+desc +'</b>: '+value + '</td>');
    };

    var tableToDisplayFloatings = function(floatingLinks) {
      if (!floatingLinks || floatingLinks.length === 0) return '';
      return $('<table/>').addClass('table')
        .append(tableContentRows(floatingLinks));
    };
    return $('<div/>').append(elyHeader(elyCode, floatingLinks.length))
      .append(tableToDisplayFloatings(floatingLinks));
  };

  var generateWorkList = function(layerName, listP) {
    var title = {
      linkProperty: 'Korjattavien linkkien lista'
    };
    $('#work-list').append('' +
      '<div style="overflow: auto;">' +
        '<div class="page">' +
          '<div class="content-box">' +
            '<header>' + title[layerName] +
              '<a class="header-link" href="#' + layerName + '">Sulje lista</a>' +
            '</header>' +
            '<div class="work-list">' +
          '</div>' +
        '</div>' +
      '</div>'
    );
    var showApp = function() {
      $('.container').show();
      $('#work-list').hide();
      $('body').removeClass('scrollable').scrollTop(0);
      $(window).off('hashchange', showApp);
    };
    $(window).on('hashchange', showApp);

    listP.then(function(floatings) {
      counter = 0;
      $('#work-list .work-list').html("");
      var floatingLinks = _.map(floatings, _.partial(floatingLinksTable, layerName));
      $('#work-list .work-list').append($('<h3 style="padding-left: 10px;"/>').html( " " + counter + " tieosoitetta on irti geometriasta")).append(floatingLinks);
      removeSpinner();
    });
  };

  var addSpinner = function () {
    $('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var removeSpinner = function(){
    jQuery('.spinner-overlay').remove();
  };

  var bindEvents = function() {
    eventbus.on('workList:select', function(layerName, listP) {
      $('#work-list').html("");
      addSpinner();
      $('.container').hide();
      $('#work-list').show();
      $('body').addClass('scrollable');
      generateWorkList(layerName, listP);
    });
  };

  root.WorkListView = {
    initialize: function() {
      bindEvents();
    }
  };

})(this);
