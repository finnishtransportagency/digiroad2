(function(root) {
  root.UserNotificationCollection = function(backend) {

    var sortedNotifications;

    this.fetchUnreadNotification = function() {
      backend.getUserNotificationInfo().then(
        function (result) {
          sortedNotifications = _.sortByOrder(result, function (notification) {
            return notification.createdDate;
          }, 'asc');

          // if (_.some(result, function (item) { return item.unRead; }))
            eventbus.trigger('userNotification:fetched', sortedNotifications) ;
        });
    };


    this.fetchAll = function() {
      return sortedNotifications;
    }
  };
})(this);
