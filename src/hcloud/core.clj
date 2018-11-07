(ns hcloud.core
  "Hetzner Cloud API functions.

  ## Auth Token

  All calls to the Hetzner API must be authenticated. For that, you
  need an Authentication Token which you can generate online in your
  Hetzner Cloud Account. The token is a string and could look like
  this:

  ```
  (def my-token \"FOOBAR123\")
  ```

  Each function takes this token as its first argument. An example
  call to `get-prices` would look like this:

  ```
  (get-prices my-token)
  ```

  ## IDs

  Furthermore, some functions take an `id` as an argument. The `id` is
  a string and refers to the resource which the API call is referring
  to (e.g., a server). An example call to `get-server` would look like
  this:

  ```
  (get-server my-token \"SERVER-ID-123\")
  ```

  ## Query Parameters

  Some functions take query string parameters. These are appended to
  the API url, i.e. in the url
  `https://api.hetzner.cloud/v1/servers?name=cx11`, there is the query
  parameter `name` with the value of `cx11`.

  In our functions, these query string parameters can be set in the
  `query-m` map. For example, the API call above is equivalent to this
  function call:

  ```
  (get-servers my-token {:name \"cx11\"})
  ```

  Pagination is also handled via the query parameters (and therefore
  the `query-m` map). Say we want to retrieve page 2 of our servers:

  ```
  (get-servers my-token {:page 2})
  ```

  ### An important note on kebab-case

  Note that these are converted from kebab-cased keywords to
  snake-cased strings. So if the Hetzner API would be expecting a
  query parameter called \"foo_bar\", we would set it by providing
  `{:foo-bar \"some-value\"}` in our map. Dashes are converted to
  underscores.

  This is also true for the replies from the Hetzner API, this time
  the other way round: The keys are snake-cased strings and will be
  converted to kebab-cased keywords. So if the reply json has a key
  \"time_series\", you'll find it in a Clojure map under the value
  `:time-series`.

  ## Body Parameters

  Some functions take (json) body parameters. In our functions, these
  can be set in the `body-m` map. Let's say we want to create a server
  with the name \"HAL\", this is how we would do it:

  ```
  (create-server my-token {:name \"HAL\"})
  ```

  Note that keywords will be converted from kebab-case to snake-case
  again, so if you would also want to set the `start_after_create`
  value, you would do this:

  ```
  (create-server my-token {:name               \"HAL\"
                           :start-after-create true})
  ```"
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk.extras]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.string :as str]))


;; -- Key Transformations ------------------------------------------------------

(defn- kebab-case-keys [m]
  (csk.extras/transform-keys csk/->kebab-case-keyword m))

(defn- snake-case-keys [m]
  (csk.extras/transform-keys csk/->snake_case_keyword m))

(defn- map->json-str [m]
  (cheshire/generate-string (snake-case-keys m)))

(defn- json-str->map [s]
  (kebab-case-keys (cheshire/parse-string s)))


;; -- General Stuff ------------------------------------------------------------

(def ^:private ^:const hetzner-api-url
  "The Hetzner API Url."
  "https://api.hetzner.cloud/v1")

(defn- api-url [& paths]
  (str/join (interpose "/" (cons hetzner-api-url paths))))

(defn- http-opts
  [{:keys [token body-m query-m]}]
  (cond-> {:accept       :json
           :as           :json
           :content-type :json
           :headers      {"Authorization" (str "Bearer " token)}}
    (not (empty? body-m))  (assoc :body (map->json-str body-m))
    (not (empty? query-m)) (assoc :query-params query-m)))


;; -- Exception Handling -------------------------------------------------------

(defn- handle-exception
  "Handle exceptions which are usually raised by clj-http.

  The `:body` of the response is parsed (the API returns a JSON
  string), keys are converted to kebab-cased keywords. Also, the
  `:http-status` of the request is `assoc`ed to the map.

  Example error data, note that the `:error` key is provided by the
  API response. As the API response body is merged as-is into the map,
  in theory other keys besides `:error` could be there:

  ```clojure
  {:error
   {:message \"invalid input in field 'server_type'\",
    :code \"invalid_input\",
    :details
    {:fields
     [{:name \"server_type\",
       :messages [\"Missing data for required field.\"]}]}},
   :http-status 422}
  ```"
  [exception]
  (let [exception-data (ex-data exception)]
    (merge (json-str->map (:body exception-data))
           {:http-status (:status exception-data)})))


;; -- Request ------------------------------------------------------------------

(defn- request [http-fn & args]
  (try
    (let [result (apply http-fn args)]
      (kebab-case-keys (:body result)))
    (catch Exception e
      (handle-exception e))))


;; -- Actions ------------------------------------------------------------------

(defn ^{:api-category :actions} get-actions
  "List all Actions

  https://docs.hetzner.cloud/#actions-list-all-actions

  Returns all action objects. You can select specific actions only and
  sort the results by using URI parameters.

  Optional query parameters (`query-m`):

   * `:status` (vector of strings): Can be used multiple
  times. Response will have only actions with specified statuses.
  Choices: running success error
   * `:sort` (vector of strings): Can be used multiple times.
  Choices: id id:asc id:desc command command:asc command:desc status
  status:asc status:desc progress progress:asc progress:desc started
  started:asc started:desc finished finished:asc finished:desc"
  ([token] (get-actions token {}))
  ([token query-m]
   (request http/get (api-url "actions")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :actions} get-action
  "Get one Action

  https://docs.hetzner.cloud/#actions-get-one-action

  Returns a specific action object.

  Parameters:

   * `id` (string): ID of the action"
  [token id]
  (request http/get (api-url "actions" id) (http-opts {:token token})))


;; -- Servers ------------------------------------------------------------------

(defn ^{:api-category :servers} get-servers
  "Get all Servers

  https://docs.hetzner.cloud/#servers-get-all-servers

  Returns all existing server objects.

  Optional query parameters (`query-m`):

   * `:name` (string): Can be used to filter servers by their
  name. The response will only contain the server matching the
  specified name."
  ([token] (get-servers token {}))
  ([token query-m]
   (request http/get (api-url "servers")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :servers} get-server
  "Get a Server

  https://docs.hetzner.cloud/#servers-get-a-server

  Returns a specific server object. The server must exist inside the
  project.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/get (api-url "servers" id) (http-opts {:token token})))

(defn ^{:api-category :servers} create-server
  "Create a Server

  https://docs.hetzner.cloud/#servers-create-a-server

  Creates a new server. Returns preliminary information about the
  server as well as an action that covers progress of creation.

  Required body parameters (`body-m`):

   * `:name` (string): Name of the server to create (must be unique
  per project and a valid hostname as per RFC 1123)
   * `:server_type` (string): ID or name of the server type this
  server should be created with
   * `:image` (string): ID or name of the image the server is created
  from

  Optional body parameters (`body-m`):

   * `:start-after-create` (boolean): Start Server right after
  creation. Defaults to true.
   * `:ssh-keys` (vector): SSH key IDs or names which should be
  injected into the server at creation time
   * `:user-data` (string): Cloud-Init user data to use during server
  creation. This field is limited to 32KiB.
   * `:location` (string): ID or name of location to create server in.
   * `:datacenter` (string): ID or name of datacenter to create server
  in."
  [token body-m]
  (request http/post (api-url "servers")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :servers} change-server-name
  "Change Name of a Server

  https://docs.hetzner.cloud/#servers-change-name-of-a-server

  Changes the name of a server.

  Please note that server names must be unique per project and valid
  hostnames as per RFC 1123 (i.e. may only contain letters, digits,
  periods, and dashes).

  Parameters:

   * `id` (string): ID of the server

  Optional body parameters (`body-m`):

   * `:name` (string): New name to set. Note: I have no idea why this
  is optional in the official Hetzner API docs."
  [token id body-m]
  (request http/put (api-url "servers" id)
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :servers} delete-server
  "Delete a Server

  https://docs.hetzner.cloud/#servers-delete-a-server

  Deletes a server. This immediately removes the server from your
  account, and it is no longer accessible.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/delete (api-url "servers" id) (http-opts {:token token})))

(defn ^{:api-category :servers} get-server-metrics
  "Get Metrics for a Server

  https://docs.hetzner.cloud/#servers-get-metrics-for-a-server

  Get Metrics for specified server.

  You must specify the type of metric to get: cpu, disk or
  network. You can also specify more than one type by comma
  separation, e.g. cpu,disk.

  Parameters:

   * `id` (string): ID of the server

  Required query parameters (`query-m`):

   * `:type` (string): Type of metrics to get (cpu, disk, network)
   * `:start` (string): Start of period to get Metrics for (in
  ISO-8601 format)
   * `:end` (string): End of period to get Metrics for (in ISO-8601
  format)

  Optional query parameters (`query-m`):

   * `:step` (number): Resolution of results in seconds "
  [token id query-m]
  (request http/get (api-url "servers" id "metrics")
           (http-opts {:token token :query-m query-m})))


;; -- Server Actions -----------------------------------------------------------

(defn ^{:api-category :server-actions} get-server-actions
  "Get all Actions for a Server

  https://docs.hetzner.cloud/#server-actions-get-all-actions-for-a-server

  Returns all action objects for a server.

  Parameters:

   * `id` (string): ID of the server

  Optional query parameters (`query-m`):

   * `:status` (vector of strings): Can be used multiple
  times. Response will have only actions with specified statuses.
  Choices: running success error
   * `:sort` (vector of strings): Can be used multiple times.
  Choices: id id:asc id:desc command command:asc command:desc status
  status:asc status:desc progress progress:asc progress:desc started
  started:asc started:desc finished finished:asc finished:desc"
  ([token] (get-server-actions token {}))
  ([token id query-m]
   (request http/get (api-url "servers" id "actions")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :server-actions} get-server-action
  "Get a specific Action for a Server

  https://docs.hetzner.cloud/#server-actions-get-a-specific-action-for-a-server

  Returns a specific action object for a Server.

  Parameters:

   * `id` (string): ID of the server
   * `action-id` (string): ID of the action"
  [token id action-id]
  (request http/get (api-url "servers" id "actions" action-id)
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} power-on-server
  "Power on a Server

  https://docs.hetzner.cloud/#server-actions-power-on-a-server

  Starts a server by turning its power on.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "poweron")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} soft-reboot-server
  "Soft-reboot a Server

  https://docs.hetzner.cloud/#server-actions-soft-reboot-a-server

  Reboots a server gracefully by sending an ACPI request. The server
  operating system must support ACPI and react to the request,
  otherwise the server will not reboot.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "reboot")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} reset-server
  "Reset a Server

  https://docs.hetzner.cloud/#server-actions-reset-a-server

  Cuts power to a server and starts it again. This forcefully stops it
  without giving the server operating system time to gracefully
  stop. This may lead to data loss, it’s equivalent to pulling the
  power cord and plugging it in again. Reset should only be used when
  reboot does not work.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "reset")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} shutdown-server
  "Shutdown a Server

  https://docs.hetzner.cloud/#server-actions-shutdown-a-server

  Shuts down a server gracefully by sending an ACPI shutdown
  request. The server operating system must support ACPI and react to
  the request, otherwise the server will not shut down.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "shutdown")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} power-off-server
  "Power off a Server

  https://docs.hetzner.cloud/#server-actions-power-off-a-server

  Cuts power to the server. This forcefully stops it without giving
  the server operating system time to gracefully stop. May lead to
  data loss, equivalent to pulling the power cord. Power off should
  only be used when shutdown does not work.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "poweroff")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} reset-server-root-password
  "Reset root Password of a Server

  https://docs.hetzner.cloud/#server-actions-reset-root-password-of-a-server

  Resets the root password. Only works for Linux systems that are
  running the qemu guest agent. Server must be powered on (state on)
  in order for this operation to succeed.

  This will generate a new password for this server and return it.

  If this does not succeed you can use the rescue system to netboot
  the server and manually change your server password by hand.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "reset_password")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} enable-server-rescue-mode
  "Enable Rescue Mode for a Server

  https://docs.hetzner.cloud/#server-actions-enable-rescue-mode-for-a-server

  Enable the Hetzner Rescue System for this server. The next time a
  Server with enabled rescue mode boots it will start a special
  minimal Linux distribution designed for repair and reinstall.

  In case a server cannot boot on its own you can use this to access a
  server’s disks.

  Rescue Mode is automatically disabled when you first boot into it or
  if you do not use it for 60 minutes.

  Enabling rescue mode will not reboot your server — you will have to
  do this yourself.

  Parameters:

   * `id` (string): ID of the server

  Optional body parameters (`body-m`):

   * `:type` (string): Type of rescue system to boot (default:
  linux64) Choices: linux64, linux32, freebsd64
   * `:ssh-keys` (vector): Array of SSH key IDs which should be
  injected into the rescue system. Only available for types: linux64
  and linux32."
  ([token] (enable-server-rescue-mode token {}))
  ([token id body-m]
   (request http/post (api-url "servers" id "actions" "enable_rescue")
            (http-opts {:token token :body-m body-m}))))

(defn ^{:api-category :server-actions} disable-server-rescue-mode
  "Disable Rescue Mode for a Server

  https://docs.hetzner.cloud/#server-actions-disable-rescue-mode-for-a-server

  Disables the Hetzner Rescue System for a server. This makes a server
  start from its disks on next reboot.

  Rescue Mode is automatically disabled when you first boot into it or
  if you do not use it for 60 minutes.

  Disabling rescue mode will not reboot your server — you will have to
  do this yourself.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "disable_rescue")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} create-server-image
  "Create Image from a Server

  https://docs.hetzner.cloud/#server-actions-create-image-from-a-server

  Creates an image (snapshot) from a server by copying the contents of
  its disks. This creates a snapshot of the current state of the disk
  and copies it into an image. If the server is currently running you
  must make sure that its disk content is consistent. Otherwise, the
  created image may not be readable.

  To make sure disk content is consistent, we recommend to shut down
  the server prior to creating an image.

  You can either create a backup image that is bound to the server and
  therefore will be deleted when the server is deleted, or you can
  create an snapshot image which is completely independent of the
  server it was created from and will survive server deletion. Backup
  images are only available when the backup option is enabled for the
  Server. Snapshot images are billed on a per GB basis.

  Parameters:

   * `id` (string): ID of the server

  Optional body parameters (`body-m`):

   * `:description` (string): Description of the image. If you do not
  set this we auto-generate one for you.
   * `:type` (string): Type of image to create (default: snapshot)
  Choices: snapshot, backup"
  ([token] (create-server-image token {}))
  ([token id body-m]
   (request http/post (api-url "servers" id "actions" "create_image")
            (http-opts {:token token :body-m body-m}))))

(defn ^{:api-category :server-actions} rebuild-server-from-image
  "Rebuild a Server from an Image

  https://docs.hetzner.cloud/#server-actions-rebuild-a-server-from-an-image

  Rebuilds a server overwriting its disk with the content of an image,
  thereby destroying all data on the target server

  The image can either be one you have created earlier (backup or
  snapshot image) or it can be a completely fresh system image
  provided by us. You can get a list of all available images with GET
  /images.

  Your server will automatically be powered off before the rebuild
  command executes.

  Parameters:

   * `id` (string): ID of the server

  Required body parameters (`body-m`):

   * `:image` (string): ID or name of image to rebuilt from."
  [token id body-m]
  (request http/post (api-url "servers" id "actions" "rebuild")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :server-actions} change-server-type
  "Change the Type of a Server

  https://docs.hetzner.cloud/#server-actions-change-the-type-of-a-server

  Changes the type (Cores, RAM and disk sizes) of a server.

  Server must be powered off for this command to succeed.

  This copies the content of its disk, and starts it again.

  You can only migrate to server types with the same storage_type and
  equal or bigger disks. Shrinking disks is not possible as it might
  destroy data.

  If the disk gets upgraded, the server type can not be downgraded any
  more. If you plan to downgrade the server type, set upgrade_disk to
  false.

  Parameters:

   * `id` (string): ID of the server

  Required body parameters (`body-m`):

   * `:server-type` (string): ID or name of server type the server
  should migrate to

  Optional body parameters (`body-m`):

   * `:upgrade-disk` (boolean): If false, do not upgrade the
  disk. This allows downgrading the server type later."
  [token id body-m]
  (request http/post (api-url "servers" id "actions" "change_type")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :server-actions} enable-server-backup
  "Enable and Configure Backups for a Server

  https://docs.hetzner.cloud/#server-actions-enable-and-configure-backups-for-a-server

  Enables and configures the automatic daily backup option for the
  server. Enabling automatic backups will increase the price of the
  server by 20%. In return, you will get seven slots where images of
  type backup can be stored.

  Backups are automatically created daily.

  Parameters:

   * `id` (string): ID of the server

  Optional body parameters (`body-m`):

   * `:backup-window` (string): Time window (UTC) in which the backup
  will run. Choices: 22-02, 02-06, 06-10, 10-14, 14-18, 18-22"
  ([token] (enable-server-backup token {}))
  ([token id body-m]
   (request http/post (api-url "servers" id "actions" "enable_backup")
            (http-opts {:token token :body-m body-m}))))

(defn ^{:api-category :server-actions} disable-server-backup
  "Disable Backups for a Server

  https://docs.hetzner.cloud/#server-actions-disable-backups-for-a-server

  Disables the automatic backup option and deletes all existing
  Backups for a Server. No more additional charges for backups will be
  made.

  Caution: This immediately removes all existing backups for the
  server!

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "disable_backup")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} attach-iso-to-server
  "Attach an ISO to a Server

  https://docs.hetzner.cloud/#server-actions-attach-an-iso-to-a-server

  Attaches an ISO to a server. The Server will immediately see it as a
  new disk. An already attached ISO will automatically be detached
  before the new ISO is attached.

  Servers with attached ISOs have a modified boot order: They will try
  to boot from the ISO first before falling back to hard disk.

  Parameters:

   * `id` (string): ID of the server

  Required body parameters (`body-m`):

   * `:iso` (string): ID or name of ISO to attach to the server as
  listed in GET /isos"
  [token id body-m]
  (request http/post (api-url "servers" id "actions" "attach_iso")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :server-actions} detach-iso-from-server
  "Detach an ISO from a Server

  https://docs.hetzner.cloud/#server-actions-detach-an-iso-from-a-server

  Detaches an ISO from a server. In case no ISO image is attached to
  the server, the status of the returned action is immediately set to
  success.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "detach_iso")
           (http-opts {:token token})))

(defn ^{:api-category :server-actions} change-server-reverse-dns-entry
  "Change reverse DNS entry for this server

  https://docs.hetzner.cloud/#server-actions-change-reverse-dns-entry-for-this-server

  Changes the hostname that will appear when getting the hostname
  belonging to the primary IPs (ipv4 and ipv6) of this server.

  Floating IPs assigned to the server are not affected by this.

  Parameters:

   * `id` (string): ID of the server

  Required body parameters (`body-m`):

   * `:ip` (string): Primary IP address for which the reverse DNS
  entry should be set.
   * `:dns-ptr` (string): Hostname to set as a reverse DNS PTR
  entry. Will reset to original value if null"
  [token id body-m]
  (request http/post (api-url "servers" id "actions" "change_dns_ptr")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :server-actions} change-server-protection
  "Change protection for a Server

  https://docs.hetzner.cloud/#server-actions-change-protection-for-a-server

  Changes the protection configuration of the server.

  Parameters:

   * `id` (string): ID of the server

  Optional body parameters (`body-m`):

   * `:delete` (boolean): If true, prevents the server from being
  deleted (currently delete and rebuild attribute needs to have the
  same value)
   * `:rebuild` (boolean): If true, prevents the server from being
  rebuilt` (currently delete and rebuild attribute needs to have the
  same value)"
  ([token] (change-server-protection token {}))
  ([token id body-m]
   (request http/post (api-url "servers" id "actions" "change_protection")
            (http-opts {:token token :body-m body-m}))))

(defn ^{:api-category :server-actions} request-server-console
  "Request Console for a Server

  https://docs.hetzner.cloud/#server-actions-request-console-for-a-server

  Requests credentials for remote access via vnc over websocket to
  keyboard, monitor, and mouse for a server. The provided url is valid
  for 1 minute, after this period a new url needs to be created to
  connect to the server. How long the connection is open after the
  initial connect is not subject to this timeout.

  Parameters:

   * `id` (string): ID of the server"
  [token id]
  (request http/post (api-url "servers" id "actions" "request_console")
           (http-opts {:token token})))


;; -- Floating IPs -------------------------------------------------------------

(defn ^{:api-category :floating-ips} get-floating-ips
  "Get all Floating IPs

  https://docs.hetzner.cloud/#floating-ips-get-all-floating-ips

  Returns all floating ip objects."
  ([token] (get-floating-ips token {}))
  ([token query-m]
   (request http/get (api-url "floating_ips")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :floating-ips} get-floating-ip
  "Get a specific Floating IP

  https://docs.hetzner.cloud/#floating-ips-get-a-specific-floating-ip

  Returns a specific floating ip object.

  Parameters:

   * `id` (string): ID of the Floating IP"
  [token id]
  (request http/get (api-url "floating_ips" id) (http-opts {:token token})))

(defn ^{:api-category :floating-ips} create-floating-ip
  "Create a Floating IP

  https://docs.hetzner.cloud/#floating-ips-create-a-floating-ip

  Creates a new Floating IP assigned to a server. If you want to
  create a Floating IP that is not bound to a server, you need to
  provide the home_location key instead of server. This can be either
  the ID or the name of the location this IP shall be created in. Note
  that a Floating IP can be assigned to a server in any location later
  on. For optimal routing it is advised to use the Floating IP in the
  same Location it was created in.

  Required body parameters (`body-m`):

   * `:type` (string): Floating IP type Choices: ipv4, ipv6

  Optional body parameters (`body-m`):

   * `:server` (number): Server to assign the Floating IP to
   * `:home-location` (string): Home location (routing is optimized
  for that location). Only optional if server argument is passed.
   * `:description` (string)"
  [token body-m]
  (request http/post (api-url "floating_ips")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :floating-ips} change-floating-ip-description
  "Change description of a Floating IP

  https://docs.hetzner.cloud/#floating-ips-change-description-of-a-floating-ip

  Changes the description of a Floating IP.

  Parameters:

   * `id` (string): ID of the Floating IP

  Optional body parameters (`body-m`):

   * `:description` (string): New Description to set. Note: I have no
  idea why this is optional in the official Hetzner API docs."
  [token id body-m]
  (request http/put (api-url "floating_ips" id)
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :floating-ips} delete-floating-ip
  "Delete a Floating IP

  https://docs.hetzner.cloud/#floating-ips-delete-a-floating-ip

  Deletes a Floating IP. If it is currently assigned to a server it
  will automatically get unassigned.

  Parameters:

   * `id`: ID of the Floating IP"
  [token id]
  (request http/delete (api-url "floating_ips" id) (http-opts {:token token})))


;; -- Floating IP Actions ------------------------------------------------------

(defn ^{:api-category :floating-ip-actions} get-floating-ip-actions
  "Get all Actions for a Floating IP

  https://docs.hetzner.cloud/#floating-ip-actions-get-all-actions-for-a-floating-ip

  Returns all action objects for a Floating IP. You can sort the
  results by using the sort URI parameter.

  Parameters:

   * `id` (string): ID of the Floating IP

  Required query parameters (`query_m`):

   * `:sort` (vector of strings): Can be used multiple times.
  Choices: id id:asc id:desc command command:asc command:desc status
  status:asc status:desc progress progress:asc progress:desc started
  started:asc started:desc finished finished:asc finished:desc"
  [token id query-m]
  (request http/get (api-url "floating_ips" id "actions")
           (http-opts {:token token :query-m query-m})))

(defn ^{:api-category :floating-ip-actions} get-floating-ip-action
  "Get an Action for a Floating IP

  https://docs.hetzner.cloud/#floating-ip-actions-get-an-action-for-a-floating-ip

  Returns a specific action object for a Floating IP.

  Parameters:

   * `id` (string): ID of the Floating IP
   * `action-id` (string): ID of the action"
  [token id action-id]
  (request http/get (api-url "floating_ips" id "actions" action-id)
           (http-opts {:token token})))

(defn ^{:api-category :floating-ip-actions} assign-floating-ip-to-server
  "Assign a Floating IP to a Server

  https://docs.hetzner.cloud/#floating-ip-actions-assign-a-floating-ip-to-a-server

  Parameters:

   * `id` (string): ID of the Floating IP

  Required body parameters (`body-m`):

   * `:server` (number): ID of the server the Floating IP shall be
  assigned to"
  [token id body-m]
  (request http/post (api-url "floating_ips" id "actions" "assign")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :floating-ip-actions} unassign-floating-ip
  "Unassign a Floating IP

  https://docs.hetzner.cloud/#floating-ip-actions-unassign-a-floating-ip

  Unassigns a Floating IP, resulting in it being unreachable. You may
  assign it to a server again at a later time.

  Parameters:

   * `id` (string): ID of the Floating IP"
  [token id]
  (request http/post (api-url "floating_ips" id "actions" "unassign")
           (http-opts {:token token})))

(defn ^{:api-category :floating-ip-actions} change-floating-ip-reverse-dns-entry
  "Change reverse DNS entry for a Floating IP

  https://docs.hetzner.cloud/#floating-ip-actions-change-reverse-dns-entry-for-a-floating-ip

  Changes the hostname that will appear when getting the hostname
  belonging to this Floating IP.

  Parameters:

   * `id` (string): ID of the Floating IP

  Required body parameters (`body-m`):

   * `:ip` (string): IP address for which to set the reverse DNS entry
   * `:dns-ptr` (string): Hostname to set as a reverse DNS PTR entry,
  will reset to original default value if null"
  [token id body-m]
  (request http/post (api-url "floating_ips" id "actions" "change_dns_ptr")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :floating-ip-actions} change-floating-ip-protection
  "Change protection

  https://docs.hetzner.cloud/#floating-ip-actions-change-protection

  Changes the protection configuration of the Floating IP.

  Parameters:

   * `id` (string): ID of the Floating IP

  Optional body parameters (`body-m`):

   * `:delete` (boolean): If true, prevents the Floating IP from being
  deleted. Note: I have no idea why this is optional in the official
  Hetzner API docs."
  [token id body-m]
  (request http/post (api-url "floating_ips" id "actions" "change_protection")
           (http-opts {:token token :body-m body-m})))


;; -- SSH Keys -----------------------------------------------------------------

(defn ^{:api-category :ssh-keys} get-ssh-keys
  "Get all SSH keys

  https://docs.hetzner.cloud/#ssh-keys-get-all-ssh-keys

  Returns all SSH key objects.

  Optional query parameters (`query_m`):

   * `:name` (string): Can be used to filter SSH keys by their
  name. The response will only contain the SSH key matching the
  specified name.
   * `:fingerprint` (string): Can be used to filter SSH keys by their
  fingerprint. The response will only contain the SSH key matching the
  specified fingerprint."
  ([token] (get-ssh-keys token {}))
  ([token query-m]
   (request http/get (api-url "ssh_keys")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :ssh-keys} get-ssh-key
  "Get an SSH key

  https://docs.hetzner.cloud/#ssh-keys-get-an-ssh-key

  Returns a specific SSH key object.

  Parameters:

   * `id` (string): ID of the SSH key"
  [token id]
  (request http/get (api-url "ssh_keys" id) (http-opts {:token token})))

(defn ^{:api-category :ssh-keys} create-ssh-key
  "Create an SSH key

  https://docs.hetzner.cloud/#ssh-keys-create-an-ssh-key

  Creates a new SSH key with the given name and public_key. Once an
  SSH key is created, it can be used in other calls such as creating
  servers.

  Required body parameters (`body-m`):

   * `:name` (string): Note: Seems to be missing in API docs.
   * `:public-key` (string): Note: Seems to be missing in API docs."
  [token body-m]
  (request http/post (api-url "ssh_keys")
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :ssh-keys} change-ssh-key-name
  "Change the name of an SSH key.

  https://docs.hetzner.cloud/#ssh-keys-change-the-name-of-an-ssh-key

  Parameters:

   * `id` (string): ID of the SSH key

  Optional body parameters (`body-m`):

   * `:name` (string): New name Name to set. Note: I have no idea why
  this is optional in the official Hetzner API docs."
  [token id body-m]
  (request http/put (api-url "ssh_keys" id)
           (http-opts {:token token :body-m body-m})))

(defn ^{:api-category :ssh-keys} delete-ssh-key
  "Delete an SSH key

  https://docs.hetzner.cloud/#ssh-keys-delete-an-ssh-key

  Deletes an SSH key. It cannot be used anymore.

  Parameters:

   * `id` (string): ID of the SSH key"
  [token id]
  (request http/delete (api-url "ssh_keys" id) (http-opts {:token token})))


;; -- Server Types -------------------------------------------------------------

(defn ^{:api-category :server-types} get-server-types
  "Get all Server Types

  https://docs.hetzner.cloud/#server-types-get-all-server-types

  Gets all server type objects.

  Optional query parameters (`query-m`):

   * `:name` (string): Can be used to filter server types by their
  name. The response will only contain the server type matching the
  specified name."
  ([token] (get-server-types {}))
  ([token query-m]
   (request http/get (api-url "server_types")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :server-types} get-server-type
  "Get a Server Type

  https://docs.hetzner.cloud/#server-types-get-a-server-type

  Gets a specific server type object.

  Parameters:

   * `id` (string): ID of server type"
  [token id]
  (request http/get (api-url "server_types" id) (http-opts {:token token})))


;; -- Locations ----------------------------------------------------------------

(defn ^{:api-category :locations} get-locations
  "Get all Locations

  https://docs.hetzner.cloud/#locations-get-all-locations

  Returns all location objects.

  Optional query parameters (`query-m`):

   * `:name` (string): Can be used to filter locations by their
  name. The response will only contain the location matching the
  specified name."
  ([token] (get-locations token {}))
  ([token query-m]
   (request http/get (api-url "locations")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :locations} get-location
  "Get a Location

  https://docs.hetzner.cloud/#locations-get-a-location

  Returns a specific location object.

  Parameters:

   * `id` (string): ID of location"
  [token id]
  (request http/get (api-url "locations" id) (http-opts {:token token})))


;; -- Datacenters --------------------------------------------------------------

(defn ^{:api-category :datacenters} get-datacenters
  "Get all Datacenters

  https://docs.hetzner.cloud/#datacenters-get-all-datacenters

  Returns all datacenter objects.

  Optional query parameters (`query-m`):

   * `:name` (string): Can be used to filter datacenters by their
  name. The response will only contain the datacenter matching the
  specified name. When the name does not match the datacenter name
  format, an invalid_input error is returned."
  ([token] (get-datacenters token {}))
  ([token query-m]
   (request http/get (api-url "datacenters")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :datacenters} get-datacenter
  "Get a Datacenter

  https://docs.hetzner.cloud/#datacenters-get-a-datacenter

  Returns a specific datacenter object.

  Parameters:

   * `id` (string): ID of datacenter"
  [token id]
  (request http/get (api-url "datacenters" id) (http-opts {:token token})))


;; -- Images -------------------------------------------------------------------

(defn ^{:api-category :images} get-images
  "Get all Images

  https://docs.hetzner.cloud/#images-get-all-images

  Returns all image objects. You can select specific image types only
  and sort the results by using URI parameters.

  Optional query parameters (`query-m`):

   * `:sort` (vector of strings): Can be used multiple times.
  Choices: id id:asc id:desc name name:asc name:desc created
  created:asc created:desc
   * `:type` (vector of strings): Can be used multiple times.
  Choices: system snapshot backup
   * `:bound-to` (string): Can be used multiple times. Server Id
  linked to the image. Only available for images of type backup
   * `:name` (string): Can be used to filter images by their name. The
  response will only contain the image matching the specified name."
  ([token] (get-images token {}))
  ([token query-m]
   (request http/get (api-url "images")
            (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :images} get-image
  "Get an Image

  https://docs.hetzner.cloud/#images-get-an-image

  Returns a specific image object.

  Parameters:

   * `id` (string): ID of the image"
  [token id]
  (request http/get (api-url "images" id) (http-opts {:token token})))

(defn ^{:api-category :images} update-image
  "Update an Image

  https://docs.hetzner.cloud/#images-update-an-image

  Updates the Image. You may change the description or convert a
  Backup image to a Snapshot Image. Only images of type snapshot and
  backup can be updated.

  Parameters:

   * `id` (string): ID of the image

  Optional body parameters (`body-m`):

   * `:description` (string): New description of Image
   * `:type` (string): Destination image type to convert to
  Choices: snapshot"
  ([token] (update-image token {}))
  ([token id body-m]
   (request http/put (api-url "images" id)
            (http-opts {:token token :body-m body-m}))))

(defn ^{:api-category :images} delete-image
  "Delete an Image

  https://docs.hetzner.cloud/#images-delete-an-image

  Deletes an Image. Only images of type snapshot and backup can be deleted.

  Parameters:

   * `id` (string): ID of the image"
  [token id]
  (request http/delete (api-url "images" id) (http-opts {:token token})))


;; -- Image Actions ------------------------------------------------------------

(defn ^{:api-category :image-actions} get-image-actions
  "Get all Actions for an Image

  https://docs.hetzner.cloud/#image-actions-get-all-actions-for-an-image

  Returns all action objects for an image. You can sort the results by
  using the sort URI parameter.

  Parameters:

   * `id` (string): ID of the Image

  Optional query parameters (`query-m`):

   * `:sort` (vector of strings): Can be used multiple times.
  Choices: id id:asc id:desc command command:asc command:desc status
  status:asc status:desc progress progress:asc progress:desc started
  started:asc started:desc finished finished:asc finished:desc"
  [token id query-m]
  (request http/get (api-url "images" id "actions")
           (http-opts {:token token :query-m query-m})))

(defn ^{:api-category :image-actions} get-image-action
  "Get an Action for an Image

  https://docs.hetzner.cloud/#image-actions-get-an-action-for-an-image

  Returns a specific action object for an image.

  Parameters:

   * `id` (string): ID of the image
   * `action-id` (string): ID of the action"
  [token id action-id]
  (request http/get (api-url "images" id "actions" action-id)
           (http-opts {:token token})))

(defn ^{:api-category :image-actions} change-image-protection
  "Change protection for an Image

  https://docs.hetzner.cloud/#image-actions-change-protection-for-an-image

  Changes the protection configuration of the image. Can only be used
  on snapshots.

  Parameters:

   * `id` (string): ID of the image

  Optional body parameters (`body-m`):

   * `:delete` (boolean): If true, prevents the snapshot from being deleted"
  ([token] (change-image-protection token {}))
  ([token id body-m]
   (request http/post (api-url "images" id "actions" "change_protection")
            (http-opts {:token token :body-m body-m}))))


;; -- ISOs ---------------------------------------------------------------------

(defn ^{:api-category :isos} get-isos
  "Get all ISOs

  https://docs.hetzner.cloud/#isos-get-all-isos

  Returns all available iso objects.

  Optional query parameters (`query-m`):

   * `:name` (string): Can be used to filter isos by their name. The
  response will only contain the iso matching the specified name."
  ([token] (get-isos token {}))
  ([token query-m]
   (request http/get (api-url "isos") (http-opts {:token token :query-m query-m}))))

(defn ^{:api-category :isos} get-iso
  "Get an ISO

  https://docs.hetzner.cloud/#isos-get-an-iso

  Returns a specific iso object.

  Parameters:

   * `id` (string): ID of the ISO"
  [token id]
  (request http/get (api-url "isos" id) (http-opts {:token token})))


;; -- Pricing ------------------------------------------------------------------

(defn ^{:api-category :pricing} get-prices
  "Get all prices

  https://docs.hetzner.cloud/#pricing-get-all-prices

  Returns prices for all resources available on the platform. VAT and
  currency of the project owner are used for calculations.

  Both net and gross prices are included in the response."
  [token]
  (request http/get (api-url "pricing") (http-opts {:token token})))


(comment
  (meta #'get-prices)
  (map (comp meta val) (ns-interns *ns*)))
