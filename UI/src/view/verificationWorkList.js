(function (root) {
  root.VerificationWorkList = function(){
    WorkListView.call(this);
    var me = this;
    this.initialize = function(){
      me.bindEvents();
    };

    this.bindEvents = function() {
      eventbus.on('verificationList:select', function(layerName, listP) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        generateWorkList(layerName, listP);
      });
    };

    var addSpinner = function () {
      $('#work-list').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function () {
      $('.spinner-overlay').remove();
    };

    var generateWorkList = function(layerName, listP) {
      var title = 'Vanhentuneiden kohteiden lista';
      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header>' + title +
        '<a class="header-link" href="#' + layerName + '">Sulje lista</a>' +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );
      addSpinner();
      listP.then(function(assets) {
        var unverifiedAssets = _.map(assets, _.partial(me.workListItemTable, layerName));
        $('#work-list .work-list').html(unverifiedAssets);
        removeSpinner();
      });
    };
  };
})(this);
